# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Terraform Remote State Backend
#
# Remote state stores terraform.tfstate in S3 instead of locally.
# This allows teams to share state and prevents conflicts.
#
# WHY USE REMOTE STATE?
#   - State is shared: anyone on the team can run terraform plan/apply
#   - DynamoDB lock prevents two people running apply at the same time
#   - State is versioned in S3 — you can roll back if needed
#
# HOW TO SET UP (one-time):
#   1. Create an S3 bucket for state:
#      aws s3 mb s3://smartfinvo-terraform-state --region us-east-2
#
#   2. Enable versioning on the bucket:
#      aws s3api put-bucket-versioning \
#        --bucket smartfinvo-terraform-state \
#        --versioning-configuration Status=Enabled
#
#   3. Create a DynamoDB table for state locking:
#      aws dynamodb create-table \
#        --table-name smartfinvo-terraform-locks \
#        --attribute-definitions AttributeName=LockID,AttributeType=S \
#        --key-schema AttributeName=LockID,KeyType=HASH \
#        --billing-mode PAY_PER_REQUEST \
#        --region us-east-2
#
#   4. Uncomment the backend block below, then run:
#      terraform init   ← will ask to migrate existing state to S3
#
# CURRENTLY: Using local state (terraform.tfstate in this folder)
# ══════════════════════════════════════════════════════════════════

# Uncomment this block once you have created the S3 bucket and
# DynamoDB table using the instructions above.

# terraform {
#   backend "s3" {
#     bucket         = "smartfinvo-terraform-state"
#     key            = "smartfinvo/terraform.tfstate"
#     region         = "us-east-2"
#     encrypt        = true
#     dynamodb_table = "smartfinvo-terraform-locks"
#   }
# }

# ── Per-Module State (Recommended for Independent Modules) ────────
# Each module can have its own state file for full independence:
#
# cicd/backend.tf:
#   backend "s3" {
#     key = "smartfinvo/cicd/terraform.tfstate"
#   }
#
# infrastructure/backend.tf:
#   backend "s3" {
#     key = "smartfinvo/infrastructure/terraform.tfstate"
#   }