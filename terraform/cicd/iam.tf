# ══════════════════════════════════════════════════════════════════
# SmartFinvo — CI/CD IAM Roles and Policies
#
# This file creates two IAM roles:
#
# 1. CodeBuild Role (codebuild_role)
#    Assumed by CodeBuild during builds.
#    Needs permissions to:
#      - Pull/push Docker images to ECR
#      - Read/write artifacts in S3
#      - Write build logs to CloudWatch
#
# 2. CodePipeline Role (codepipeline_role)
#    Assumed by CodePipeline when running the pipeline.
#    Needs permissions to:
#      - Read/write artifacts in S3
#      - Trigger CodeBuild builds
#      - Use the GitHub CodeStar connection
#
# IAM Principle of Least Privilege:
#   Each role has ONLY the permissions it needs — nothing more.
#   Specific resource ARNs are used wherever possible.
# ══════════════════════════════════════════════════════════════════

# ══════════════════════════════════════════════════════════════════
# CodeBuild IAM Role
# ══════════════════════════════════════════════════════════════════

# The role itself — allows CodeBuild service to assume it
resource "aws_iam_role" "codebuild_role" {
  name = "${var.project_name}-codebuild-role"

  # Trust policy: only the CodeBuild service can assume this role
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codebuild.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name        = "${var.project_name} CodeBuild Role"
    Environment = "production"
    Project     = var.project_name
  }
}

# ── CodeBuild Policy 1: ECR Permissions ───────────────────────────
# Allows CodeBuild to log in, pull base images, and push built images
# to Elastic Container Registry (ECR).
#
# GetAuthorizationToken = docker login equivalent
# BatchCheck + GetDownload = docker pull
# PutImage + InitiateUpload + UploadPart + CompleteUpload = docker push

resource "aws_iam_role_policy" "codebuild_ecr_policy" {
  name = "${var.project_name}-codebuild-ecr-policy"
  role = aws_iam_role.codebuild_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
        Resource = "*"
        # GetAuthorizationToken requires * (it's account-level, not repo-level)
        # Other actions could be scoped to specific ECR repo ARN if preferred
      }
    ]
  })
}

# ── CodeBuild Policy 2: S3 Artifact Access ────────────────────────
# Allows CodeBuild to read source artifacts from S3 (put there by
# CodePipeline) and write build output artifacts back to S3.
# Scoped to only the artifacts bucket — not all S3 buckets.

resource "aws_iam_role_policy" "codebuild_s3_policy" {
  name = "${var.project_name}-codebuild-s3-policy"
  role = aws_iam_role.codebuild_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${var.artifacts_bucket_name}",
          "arn:aws:s3:::${var.artifacts_bucket_name}/*"
        ]
      }
    ]
  })
}

# ── CodeBuild Policy 3: CloudWatch Logs ───────────────────────────
# Allows CodeBuild to create log groups and write build output to
# CloudWatch Logs. Scoped to only CodeBuild log groups.
# View logs: AWS Console → CloudWatch → Log groups → /aws/codebuild/

resource "aws_iam_role_policy" "codebuild_logs_policy" {
  name = "${var.project_name}-codebuild-logs-policy"
  role = aws_iam_role.codebuild_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        # Scoped to CodeBuild log groups in this account and region
        Resource = "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/aws/codebuild/*"
      }
    ]
  })
}

# ══════════════════════════════════════════════════════════════════
# CodePipeline IAM Role
# ══════════════════════════════════════════════════════════════════

# The role itself — allows CodePipeline service to assume it
resource "aws_iam_role" "codepipeline_role" {
  name = "${var.project_name}-codepipeline-role"

  # Trust policy: only the CodePipeline service can assume this role
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codepipeline.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name        = "${var.project_name} CodePipeline Role"
    Environment = "production"
    Project     = var.project_name
  }
}

# ── CodePipeline Policy 1: S3 Artifact Store ──────────────────────
# CodePipeline reads/writes artifacts between stages.
# GetObjectVersion needed to handle S3 versioning.
# Scoped to only the artifacts bucket.

resource "aws_iam_role_policy" "codepipeline_s3_policy" {
  name = "${var.project_name}-codepipeline-s3-policy"
  role = aws_iam_role.codepipeline_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:GetObjectVersion",
          "s3:ListBucket"
        ]
        Resource = [
          "arn:aws:s3:::${var.artifacts_bucket_name}",
          "arn:aws:s3:::${var.artifacts_bucket_name}/*"
        ]
      }
    ]
  })
}

# ── CodePipeline Policy 2: CodeBuild Trigger ──────────────────────
# Allows CodePipeline to start CodeBuild jobs and check their status.
# Scoped to only the smartfinvo-build project — not all CodeBuild projects.

resource "aws_iam_role_policy" "codepipeline_codebuild_policy" {
  name = "${var.project_name}-codepipeline-codebuild-policy"
  role = aws_iam_role.codepipeline_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "codebuild:BatchGetBuilds",
          "codebuild:BatchGetBuildBatches",
          "codebuild:StartBuild",
          "codebuild:StartBuildBatch"
        ]
        # Scoped to only our build project ARN
        Resource = "arn:aws:codebuild:${var.aws_region}:${var.aws_account_id}:project/${var.project_name}-build"
      }
    ]
  })
}

# ── CodePipeline Policy 3: GitHub Connection ──────────────────────
# Allows CodePipeline to use the CodeStar connection to GitHub.
# UseConnection = read repository and receive webhook events.
# Resource * needed because connection ARN is dynamic (known after apply).

resource "aws_iam_role_policy" "codepipeline_codestar_policy" {
  name = "${var.project_name}-codepipeline-codestar-policy"
  role = aws_iam_role.codepipeline_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "codestar-connections:UseConnection"
        ]
        Resource = "*"
        # Could be scoped to aws_codestarconnections_connection.github.arn
        # but that creates a circular dependency — connection ARN needs to
        # exist before the policy, but policy needs to exist before pipeline
      }
    ]
  })
}