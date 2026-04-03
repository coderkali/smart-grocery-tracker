# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Root Provider Configuration
#
# This file contains ONLY the AWS provider configuration.
# The terraform{} block with version constraints lives in versions.tf.
# All resources are managed inside sub-modules:
#
#   cicd/           — CodePipeline, CodeBuild, S3 artifacts, IAM
#   infrastructure/ — EKS, VPC data sources, WAF, budgets, alerts
#
# HOW TO USE:
#   Deploy CI/CD pipeline independently:
#     cd terraform/cicd/
#     terraform init && terraform apply
#
#   Deploy infrastructure independently:
#     cd terraform/infrastructure/
#     terraform init && terraform apply
#
# Credentials come from AWS CLI configuration.
# Run "aws configure" if not already set up.
# ══════════════════════════════════════════════════════════════════

# Configure the AWS provider
# Region is hardcoded here at root level — each module also declares
# its own provider with a region variable for independent deployments.
provider "aws" {
  # Which AWS region to create resources in
  # Must match where our VPC and ECR already exist
  region = "us-east-2"

  # No access keys here — Terraform reads from:
  #   ~/.aws/credentials (set by aws configure)
  # This is the secure approach — never hardcode keys
}