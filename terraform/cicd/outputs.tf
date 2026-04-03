# ══════════════════════════════════════════════════════════════════
# SmartFinvo — CI/CD Module Outputs
#
# Values printed after: terraform apply
# View anytime with:    terraform output
#
# These outputs are useful for:
#   - Bookmarking AWS Console URLs
#   - Scripting — other tools can read these values
#   - Verifying the deployment succeeded
# ══════════════════════════════════════════════════════════════════

# ── Pipeline Outputs ──────────────────────────────────────────────

output "pipeline_name" {
  description = "CodePipeline name — use to find it in AWS Console"
  value       = aws_codepipeline.smartfinvo_pipeline.name
}

output "pipeline_arn" {
  description = "Full ARN of the CodePipeline — use for IAM or EventBridge rules"
  value       = aws_codepipeline.smartfinvo_pipeline.arn
}

# ── CodeBuild Outputs ─────────────────────────────────────────────

output "codebuild_project_name" {
  description = "CodeBuild project name — use to start manual builds"
  value       = aws_codebuild_project.smartfinvo_build.name
}

output "codebuild_project_arn" {
  description = "Full ARN of the CodeBuild project"
  value       = aws_codebuild_project.smartfinvo_build.arn
}

output "codebuild_start_command" {
  description = "CLI command to manually trigger a CodeBuild build"
  value       = "aws codebuild start-build --project-name ${aws_codebuild_project.smartfinvo_build.name} --region ${var.aws_region}"
}

# ── Artifacts Bucket Outputs ──────────────────────────────────────

output "artifacts_bucket_name" {
  description = "S3 bucket storing pipeline artifacts"
  value       = aws_s3_bucket.pipeline_artifacts.bucket
}

output "artifacts_bucket_arn" {
  description = "ARN of the pipeline artifacts S3 bucket"
  value       = aws_s3_bucket.pipeline_artifacts.arn
}

# ── GitHub Connection Outputs ─────────────────────────────────────

output "github_connection_arn" {
  description = "CodeStar connection ARN — status must be AVAILABLE (not PENDING) to work"
  value       = aws_codestarconnections_connection.github.arn
}

output "github_connection_status" {
  description = "Connection status — must be AVAILABLE. If PENDING, activate it in AWS Console"
  value       = aws_codestarconnections_connection.github.connection_status
}

# ── Console URLs ──────────────────────────────────────────────────

output "pipeline_console_url" {
  description = "Direct link to view this pipeline in AWS Console"
  value       = "https://${var.aws_region}.console.aws.amazon.com/codesuite/codepipeline/pipelines/${aws_codepipeline.smartfinvo_pipeline.name}/view"
}

output "codebuild_console_url" {
  description = "Direct link to view CodeBuild project in AWS Console"
  value       = "https://${var.aws_region}.console.aws.amazon.com/codesuite/codebuild/${var.aws_account_id}/projects/${aws_codebuild_project.smartfinvo_build.name}/history"
}

output "github_connection_console_url" {
  description = "Direct link to activate the pending GitHub connection in AWS Console"
  value       = "https://${var.aws_region}.console.aws.amazon.com/codesuite/settings/${var.aws_account_id}/${var.aws_region}/connections"
}