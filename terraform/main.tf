# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Terraform Configuration
#
# This file tells Terraform:
#   1. Which version of Terraform to use
#   2. Which AWS plugin (provider) to download
#   3. Which AWS region to connect to
#
# Credentials come from AWS CLI configuration.
# Run "aws configure" if not already set up.
# ══════════════════════════════════════════════════════════════════

terraform {
  # Minimum Terraform version required
  required_version = ">= 1.5.0"

  required_providers {
    # AWS provider — plugin that knows how to talk to AWS
    # hashicorp/aws = official AWS provider maintained by HashiCorp
    # ~> 5.0 means: use version 5.x (any 5.x update is fine)
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Configure the AWS provider
provider "aws" {
  # Which AWS region to create resources in
  # Must match where our VPC and ECR already exist
  region = var.aws_region

  # No access keys here — Terraform reads from:
  #   ~/.aws/credentials (set by aws configure)
  # This is the secure approach — never hardcode keys
}
# ══════════════════════════════════════════════════════════════════
# S3 Bucket for CodePipeline Artifacts
# ══════════════════════════════════════════════════════════════════

resource "aws_s3_bucket" "pipeline_artifacts" {
  bucket = "smartfinvo-pipeline-artifacts-2026"

  tags = {
    Name        = "SmartFinvo Pipeline Artifacts"
    Environment = "production"
    Project     = "SmartFinvo"
  }
}

# Block all public access (security)
resource "aws_s3_bucket_public_access_block" "pipeline_artifacts" {
  bucket = aws_s3_bucket.pipeline_artifacts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Enable server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "pipeline_artifacts" {
  bucket = aws_s3_bucket.pipeline_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
