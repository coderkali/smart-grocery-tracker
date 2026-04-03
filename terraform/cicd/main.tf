# ══════════════════════════════════════════════════════════════════
# SmartFinvo — CI/CD Module
#
# This module manages the complete build and deployment pipeline:
#   1. S3 bucket        — stores artifacts between pipeline stages
#   2. CodeStar         — GitHub connection (OAuth handshake)
#   3. CodeBuild        — builds Docker image and pushes to ECR
#   4. CodePipeline     — orchestrates Source → Build stages
#
# INDEPENDENT MODULE: Can be deployed without the infrastructure/
# module. EKS does not need to exist for this to work.
#
# DEPLOY:
#   cd terraform/cicd/
#   terraform init
#   terraform plan
#   terraform apply
#
# POST-DEPLOY (required once):
#   After apply, go to AWS Console → Developer Tools → Connections
#   Find "smartfinvo-github-connection" → click "Update pending connection"
#   Complete the GitHub OAuth flow to activate the connection.
# ══════════════════════════════════════════════════════════════════

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# AWS provider — credentials come from ~/.aws/credentials (aws configure)
# Never hardcode access keys here
provider "aws" {
  region = var.aws_region
}

# ══════════════════════════════════════════════════════════════════
# S3 Bucket — Pipeline Artifact Storage
#
# Stores intermediate build artifacts passed between pipeline stages:
#   Stage 1 (Source) writes  → source_output.zip
#   Stage 2 (Build)  reads   → source_output.zip, writes build_output.zip
#
# Security:
#   - All public access blocked
#   - AES-256 encryption at rest
#   - Access controlled by CodeBuild and CodePipeline IAM roles
# ══════════════════════════════════════════════════════════════════

resource "aws_s3_bucket" "pipeline_artifacts" {
  bucket = var.artifacts_bucket_name

  tags = {
    Name        = "${var.project_name} Pipeline Artifacts"
    Environment = "production"
    Project     = var.project_name
  }
}

# Block all public access — artifacts contain source code, never expose publicly
resource "aws_s3_bucket_public_access_block" "pipeline_artifacts" {
  bucket = aws_s3_bucket.pipeline_artifacts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Encrypt all artifacts at rest using AES-256
resource "aws_s3_bucket_server_side_encryption_configuration" "pipeline_artifacts" {
  bucket = aws_s3_bucket.pipeline_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ══════════════════════════════════════════════════════════════════
# CodeStar Connection — GitHub Integration
#
# Creates a pending OAuth connection to GitHub.
# CodePipeline uses this to receive push event triggers and pull code.
#
# IMPORTANT: After terraform apply, this connection is in PENDING state.
# You must manually activate it:
#   AWS Console → Developer Tools → Connections → Update pending connection
# ══════════════════════════════════════════════════════════════════

resource "aws_codestarconnections_connection" "github" {
  name          = "${var.project_name}-github-connection"
  provider_type = "GitHub"

  tags = {
    Name        = "${var.project_name} GitHub Connection"
    Environment = "production"
    Project     = var.project_name
  }
}

# ══════════════════════════════════════════════════════════════════
# CodeBuild Project — Docker Image Builder
#
# Triggered by CodePipeline when source code changes.
# Runs buildspec.yml from the repository root which:
#   1. Authenticates to ECR
#   2. Builds the Docker image
#   3. Tags and pushes image to ECR
#   4. Outputs build artifacts for downstream stages
#
# Logs available at:
#   CloudWatch → Log groups → /aws/codebuild/smartfinvo-build
# ══════════════════════════════════════════════════════════════════

resource "aws_codebuild_project" "smartfinvo_build" {
  name          = "${var.project_name}-build"
  service_role  = aws_iam_role.codebuild_role.arn
  build_timeout = 30  # minutes — increase if builds take longer

  environment {
    # BUILD_GENERAL1_MEDIUM = 3 GB RAM, 2 vCPUs — sufficient for Docker builds
    # Use BUILD_GENERAL1_LARGE if builds are slow or run out of memory
    compute_type                = "BUILD_GENERAL1_MEDIUM"
    image                       = "aws/codebuild/amazonlinux2-x86_64-standard:5.0"
    type                        = "LINUX_CONTAINER"
    image_pull_credentials_type = "CODEBUILD"
  }

  source {
    type            = "GITHUB"
    location        = var.github_repo_url
    git_clone_depth = 1  # shallow clone — only latest commit, saves time
  }

  artifacts {
    type = "NO_ARTIFACTS"  # artifacts passed through S3 by CodePipeline
  }

  logs_config {
    cloudwatch_logs {
      group_name  = "/aws/codebuild/${var.project_name}-build"
      stream_name = "build-logs"
      status      = "ENABLED"
    }
  }

  tags = {
    Name        = "${var.project_name} Build Project"
    Environment = "production"
    Project     = var.project_name
  }
}

# ══════════════════════════════════════════════════════════════════
# CodePipeline — Pipeline Orchestration
#
# Two-stage pipeline:
#   Stage 1 (Source): Monitors GitHub main branch for pushes
#                     Downloads source code into artifacts bucket
#   Stage 2 (Build):  Triggers CodeBuild with the source artifact
#                     CodeBuild builds + pushes Docker image to ECR
#
# Pipeline triggers automatically on every push to var.github_branch.
# Monitor pipeline runs:
#   AWS Console → CodePipeline → smartfinvo-pipeline
# ══════════════════════════════════════════════════════════════════

resource "aws_codepipeline" "smartfinvo_pipeline" {
  name     = "${var.project_name}-pipeline"
  role_arn = aws_iam_role.codepipeline_role.arn

  # Where pipeline artifacts are stored between stages
  artifact_store {
    location = aws_s3_bucket.pipeline_artifacts.bucket
    type     = "S3"
  }

  # Stage 1: Pull source code from GitHub
  stage {
    name = "Source"

    action {
      category         = "Source"
      name             = "SourceAction"
      owner            = "AWS"
      provider         = "CodeStarConnections"
      version          = "1"
      output_artifacts = ["source_output"]

      configuration = {
        FullRepositoryId = var.github_repo_id    # "coderkali/smart-grocery-tracker"
        BranchName       = var.github_branch     # "main"
        ConnectionArn    = aws_codestarconnections_connection.github.arn
      }
    }
  }

  # Stage 2: Build Docker image using CodeBuild
  stage {
    name = "Build"

    action {
      name             = "BuildAction"
      category         = "Build"
      owner            = "AWS"
      provider         = "CodeBuild"
      version          = "1"
      input_artifacts  = ["source_output"]
      output_artifacts = ["build_output"]

      configuration = {
        ProjectName = aws_codebuild_project.smartfinvo_build.name
      }
    }
  }

  tags = {
    Name        = "${var.project_name} Pipeline"
    Environment = "production"
    Project     = var.project_name
  }

  # Ensure IAM policies are attached before pipeline tries to run
  depends_on = [
    aws_iam_role_policy.codepipeline_s3_policy,
    aws_iam_role_policy.codepipeline_codebuild_policy,
    aws_iam_role_policy.codepipeline_codestar_policy
  ]
}