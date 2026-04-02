# ══════════════════════════════════════════════════════════════════
# IAM Roles for CodeBuild and CodePipeline
# ══════════════════════════════════════════════════════════════════

resource "aws_iam_role" "codebuild_role" {
  name = "smartfinvo-codebuild-role"
  assume_role_policy = jsondecode({
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
    Name = "SmartFinvo CodeBuild Role"
    Environment = "production"
    Project     = "SmartFinvo"
  }
}

resource "aws_iam_role_policy" "codebuild_ecr_policy" {
  name = "codebuild-ecr-policy"
  role = aws_iam_role.codebuild_role.id
  policy = jsondecode({
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
      }
    ]
  })
}

# CodeBuild Role Policy - S3 permissions
resource "aws_iam_role_policy" "codebuild_s3_policy" {
  name   = "codebuild-s3-policy"
  role   = aws_iam_role.codebuild_role.id
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
          "arn:aws:s3:::smartfinvo-pipeline-artifacts-2026",
          "arn:aws:s3:::smartfinvo-pipeline-artifacts-2026/*"
        ]
      }
    ]
  })
}

# CodeBuild Role Policy - CloudWatch logs
resource "aws_iam_role_policy" "codebuild_logs_policy" {
  name   = "codebuild-logs-policy"
  role   = aws_iam_role.codebuild_role.id
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
        Resource = "arn:aws:logs:us-east-2:274214919013:log-group:/aws/codebuild/*"
      }
    ]
  })
}

# ══════════════════════════════════════════════════════════════════
# CodePipeline IAM Role
# ══════════════════════════════════════════════════════════════════

resource "aws_iam_role" "codepipeline_role" {
  name = "smartfinvo-codepipeline-role"

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
    Name        = "SmartFinvo CodePipeline Role"
    Environment = "production"
    Project     = "SmartFinvo"
  }
}

# CodePipeline Role Policy - S3 permissions (artifact store)
resource "aws_iam_role_policy" "codepipeline_s3_policy" {
  name   = "codepipeline-s3-policy"
  role   = aws_iam_role.codepipeline_role.id
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
          "arn:aws:s3:::smartfinvo-pipeline-artifacts-2026",
          "arn:aws:s3:::smartfinvo-pipeline-artifacts-2026/*"
        ]
      }
    ]
  })
}

# CodePipeline Role Policy - CodeBuild permissions
resource "aws_iam_role_policy" "codepipeline_codebuild_policy" {
  name   = "codepipeline-codebuild-policy"
  role   = aws_iam_role.codepipeline_role.id
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
        Resource = "arn:aws:codebuild:us-east-2:274214919013:project/smartfinvo-build"
      }
    ]
  })
}

# CodePipeline Role Policy - CodeConnections (GitHub) permissions
resource "aws_iam_role_policy" "codepipeline_codestar_policy" {
  name   = "codepipeline-codestar-policy"
  role   = aws_iam_role.codepipeline_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "codestar-connections:UseConnection"
        ]
        Resource = "*"
      }
    ]
  })
}