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