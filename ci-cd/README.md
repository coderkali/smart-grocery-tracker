# CI/CD Pipeline Files

Pipeline configuration for CodeBuild and GitHub Actions.

## Files

| File | Purpose |
|------|---------|
| `buildspec.yml` | AWS CodeBuild build specification |
| `../terraform/cicd/` | Terraform config for CodeBuild + CodePipeline infrastructure |

## Pipeline Flow

1. Push to `main` → triggers CodePipeline
2. CodeBuild runs `buildspec.yml`
3. Builds Docker image → pushes to ECR
4. Deploys to EKS

## GitHub Actions

See `.github/workflows/ci.yml` for the GitHub Actions CI pipeline.