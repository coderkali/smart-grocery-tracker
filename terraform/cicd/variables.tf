# ══════════════════════════════════════════════════════════════════
# SmartFinvo — CI/CD Module Variables
#
# All configurable values for the CI/CD module in one place.
# Change here → everything in this module updates automatically.
#
# This module is SELF-CONTAINED — no references to the
# infrastructure/ module. Can be deployed independently.
# ══════════════════════════════════════════════════════════════════

# ── AWS Configuration ─────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region where CI/CD resources are deployed"
  type        = string
  default     = "us-east-2"
  # Must match the region where ECR repositories exist
}

variable "aws_account_id" {
  description = "AWS account ID — used to construct IAM policy ARNs"
  type        = string
  default     = "274214919013"
  # Find yours with: aws sts get-caller-identity --query Account --output text
}

# ── Project Configuration ─────────────────────────────────────────

variable "project_name" {
  description = "Project name prefix — used to name all CI/CD resources"
  type        = string
  default     = "smartfinvo"
  # Example: "smartfinvo" → creates "smartfinvo-build", "smartfinvo-pipeline" etc.
}

# ── S3 Artifact Storage ───────────────────────────────────────────

variable "artifacts_bucket_name" {
  description = "S3 bucket name for storing CodePipeline artifacts between stages"
  type        = string
  default     = "smartfinvo-pipeline-artifacts-2026"
  # Must be globally unique across all AWS accounts
  # Artifacts are encrypted at rest (AES-256) and never public
}

# ── GitHub Integration ────────────────────────────────────────────

variable "github_repo_url" {
  description = "Full HTTPS URL of the GitHub repository (used by CodeBuild to clone)"
  type        = string
  default     = "https://github.com/coderkali/smart-grocery-tracker.git"
}

variable "github_repo_id" {
  description = "GitHub repository in owner/repo format (used by CodePipeline source action)"
  type        = string
  default     = "coderkali/smart-grocery-tracker"
  # Format must be exactly: "owner/repository-name"
}

variable "github_branch" {
  description = "Branch name that triggers the CI/CD pipeline on push"
  type        = string
  default     = "main"
  # Push to this branch → pipeline automatically starts
}