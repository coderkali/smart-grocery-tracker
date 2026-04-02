# ══════════════════════════════════════════════════════════════════
# CodePipeline
# ══════════════════════════════════════════════════════════════════

resource "aws_codepipeline" "smartfinvo_pipeline" {
  name = "smartfinvo-pipeline"
  role_arn = aws_iam_role.codepipeline_role.arn

  artifact_store {
    location = aws_s3_bucket.pipeline_artifacts.bucket
    type     = "S3"
  }

  stage {
    name = "Source"

    action {
      category = "Source"
      name     = "SourceAction"
      owner    = "AWS"
      provider = "CodeStarConnections"
      version  = "1"
      output_artifacts = ["source_output"]

      configuration = {
        FullRepositoryId = "coderkali/smart-grocery-tracker"
        BranchName       = "main"
        ConnectionArn    =  aws_codestarconnections_connection.github.arn
      }
    }
  }

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
    Name        = "SmartFinvo Pipeline"
    Environment = "production"
    Project     = "SmartFinvo"
  }

  depends_on = [
    aws_iam_role_policy.codepipeline_s3_policy,
    aws_iam_role_policy.codepipeline_codebuild_policy,
    aws_iam_role_policy.codepipeline_codestar_policy
  ]
}

# CodeStar Connections for GitHub
resource "aws_codestarconnections_connection" "github" {
  name          = "smartfinvo-github-connection"
  provider_type = "GitHub"

  tags = {
    Name        = "SmartFinvo GitHub Connection"
    Environment = "production"
    Project     = "SmartFinvo"
  }
}