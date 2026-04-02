# ══════════════════════════════════════════════════════════════════
# CodeBuild Project
# ══════════════════════════════════════════════════════════════════


resource "aws_codebuild_project" "smartfinvo_build" {
  name = "smartfinvo-build"
  service_role =  aws_iam_role.codebuild_role.arn
  build_timeout = 30

  environment {
    compute_type = "BUILD_GENERAL1_MEDIUM"
    image        = "aws/codebuild/amazonlinux2-x86_64-standard:5.0"
    type         = "LINUX_CONTAINER"
    image_pull_credentials_type = "CODEBUILD"
  }
  source {
    type      = "GITHUB"
    location  = "https://github.com/coderkali/smart-grocery-tracker.git"
    git_clone_depth = 1
  }

  tags = {
    Name        = "SmartFinvo Build Project"
    Environment = "production"
    Project     = "SmartFinvo"
  }

}