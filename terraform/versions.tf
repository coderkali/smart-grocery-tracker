# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Root Terraform Version Constraints
#
# This file documents the required Terraform and provider versions.
# Each sub-module (cicd/ and infrastructure/) also declares its own
# versions block — this root file is purely for reference.
#
# To use a sub-module independently:
#   cd cicd/          → terraform init && terraform apply
#   cd infrastructure/ → terraform init && terraform apply
# ══════════════════════════════════════════════════════════════════

terraform {
  # Minimum Terraform CLI version required across all modules
  required_version = ">= 1.5.0"

  required_providers {
    # AWS provider — official plugin maintained by HashiCorp
    # ~> 5.0 = any 5.x patch version is acceptable
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}