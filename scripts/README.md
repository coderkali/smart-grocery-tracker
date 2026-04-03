# Scripts

Shell scripts organized by purpose for managing the SmartFinvo platform.

## Folder Structure

| Folder | Purpose |
|--------|---------|
| `start/` | Start the EKS cluster and all services |
| `stop/` | Gracefully stop and destroy EKS resources |
| `deploy/` | Deploy application to Kubernetes |
| `setup/` | One-time AWS account setup and protections |
| `maintenance/` | Backups, cleanup, health checks |
| `testing/` | Run test suites |
| `monitoring/` | Check pod health, resource usage, alerts |

## Daily Workflow

```bash
# Morning: Start the cluster (~10-12 min)
./scripts/start/start-cluster.sh

# Deploy app
./scripts/deploy/deploy.sh

# Evening: Stop everything (saves AWS costs)
./scripts/stop/stop-cluster.sh
```

## Important Notes

- `setup/setup-aws.sh` is run ONCE to create permanent protections (SNS, IAM, Budget, WAF)
- `stop/stop-cluster.sh` only destroys EKS — it preserves Budget, WAF, and IAM protections
- Always run `pre-deploy-health-check.sh` before `deploy.sh` in production