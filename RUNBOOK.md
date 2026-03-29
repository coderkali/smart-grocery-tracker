# SmartFinvo — Runbook

Complete fix history, deployment commands, and troubleshooting reference.

---

## Table of Contents

1. [Daily Workflow](#daily-workflow)
2. [Cluster Status Verification](#cluster-status-verification)
3. [Issue 1 — Git Commit Email Typo](#issue-1--git-commit-email-typo)
4. [Issue 2 — EKS Node Group CREATE_FAILED](#issue-2--eks-node-group-create_failed-t3medium-not-free-tier)
5. [Issue 3 — AWS Budget Action Validation Error](#issue-3--aws-budget-action-validation-error)
6. [Issue 4 — stop.sh Deleted Permanent Resources](#issue-4--stopsh-deleted-budgetiamwaf-permanent-resources)
7. [Issue 5 — EBS CSI Driver Stuck in CREATING](#issue-5--ebs-csi-driver-stuck-in-creating-20-minutes)
8. [Issue 6 — Redis Pod Stuck in Pending](#issue-6--redis-pod-stuck-in-pending-too-many-pods)
9. [Issue 7 — App Stuck at 1/2 Replicas](#issue-7--app-stuck-at-12-replicas-hpa-overriding)
10. [Health Check Commands](#health-check-commands)

---

## Daily Workflow

```bash
# Morning — start the cluster (~10-12 min)
cd /Users/kaliprasad/Documents/Project/smart-grocery-tracker
./start.sh

# Deploy the app (only needed after code changes)
./deploy.sh

# Evening — stop the cluster to avoid charges
./stop.sh
```

**Cost:** ~$0.15/hour while running. Stop every day to keep monthly bill ~$9.

---

## Cluster Status Verification

```bash
# Check EKS cluster status
aws eks describe-cluster \
  --name smartfinvo \
  --region us-east-2 \
  --query "cluster.status"
# Expected: "ACTIVE"

# Check node group status
aws eks describe-nodegroup \
  --cluster-name smartfinvo \
  --nodegroup-name smartfinvo-nodes \
  --region us-east-2 \
  --query "nodegroup.status"
# Expected: "ACTIVE"

# Check EC2 instance running
aws ec2 describe-instances \
  --region us-east-2 \
  --filters "Name=instance-state-name,Values=running" \
  --query "Reservations[].Instances[].[InstanceId,InstanceType,State.Name]" \
  --output table
# Expected: 1 x t3.small running

# Connect kubectl to cluster
aws eks update-kubeconfig \
  --region us-east-2 \
  --name smartfinvo

# Check node is Ready
kubectl get nodes
# Expected: STATUS = Ready

# Check all app pods
kubectl get pods -n smartfinvo
# Expected: postgres, redis, smartfinvo all Running

# Check system pods
kubectl get pods -n kube-system
```

---

## Issue 1 — Git Commit Email Typo

**Problem:** All commits had `coderkali@gmail.copm` (typo) instead of `coderkali@gmail.com`

**Fix:**
```bash
# Fix email in git config
git config user.email "coderkali@gmail.com"
git config user.name "Kali Prasad"

# Rewrite ALL past commits with correct author
git rebase --root --exec 'git commit --amend --reset-author --no-edit'

# Force push rewritten history to GitHub
git push --force origin main
```

---

## Issue 2 — EKS Node Group CREATE_FAILED (t3.medium not Free Tier)

**Problem:** AWS blocked t3.medium — account was restricted to Free Tier eligible instances only.

**Fix:** Changed `terraform/variables.tf`:
```hcl
# Before
node_instance_type = "t3.medium"
node_desired_count = 2

# After
node_instance_type = "t3.small"
node_desired_count = 1
node_max_count     = 2
```

```bash
# Verify the plan first
cd terraform && terraform plan

# Apply the change
terraform apply -auto-approve
```

---

## Issue 3 — AWS Budget Action Validation Error

**Problem:** `ValidationException` when creating Budget Action — wildcard `"*"` not supported for SSM `stop_ec2` instance IDs.

**Fix:** Removed the SSM stop_ec2 action from `terraform/budget.tf` entirely. Replaced with an IAM deny policy that auto-attaches at 100% of budget spend.

```bash
terraform apply -auto-approve
```

---

## Issue 4 — stop.sh Deleted Budget/IAM/WAF (Permanent Resources)

**Problem:** Original `stop.sh` ran full `terraform destroy` which deleted account-level cost protections (Budget, IAM guardrails, WAF, SNS).

**Fix:** Split infrastructure into permanent vs daily resources using 3 scripts:

| Script | Purpose | When to run |
|--------|---------|-------------|
| `setup.sh` | Creates Budget, WAF, IAM, SNS | Once ever |
| `start.sh` | Creates EKS cluster + node | Every morning |
| `stop.sh` | Destroys EKS cluster + node only | Every evening |

```bash
# Run ONCE to set up permanent account protections
chmod +x setup.sh && ./setup.sh

# Daily workflow
chmod +x start.sh stop.sh deploy.sh
./start.sh    # morning — creates cluster
./deploy.sh   # deploy app
./stop.sh     # evening — destroys cluster only
```

---

## Issue 5 — EBS CSI Driver Stuck in CREATING (20+ minutes)

**Problem:** `aws-ebs-csi-driver` addon timed out — pods crashed with IAM credential error. PostgreSQL PVC could not be provisioned without this driver.

**Root cause found with:**
```bash
# Check addon status
aws eks describe-addon \
  --cluster-name smartfinvo \
  --addon-name aws-ebs-csi-driver \
  --region us-east-2 \
  --query "addon.{status:status,health:health}"

# Check which pods are failing
kubectl get pods -n kube-system | grep ebs

# Read pod logs to find actual error
kubectl logs -n kube-system <ebs-csi-controller-pod-name> \
  -c csi-provisioner | tail -20
```

**Error found:**
```
no EC2 IMDS role found — operation error ec2imds: GetMetadata, context deadline exceeded
```

**Root cause explanation:**
By default AWS allows only 1 network hop to reach the EC2 metadata service (IMDS). Pods are 1 extra hop away from IMDS compared to the EC2 instance itself, so they get blocked. The EBS CSI driver pod needs to reach IMDS to get IAM credentials to provision EBS volumes.

**Fix 1 — Attach EBS CSI policy to node IAM role:**
```bash
aws iam attach-role-policy \
  --role-name smartfinvo-eks-node-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy
```

**Fix 2 — Increase IMDS hop limit from 1 to 2:**
```bash
# Get the running EC2 instance ID
aws ec2 describe-instances \
  --region us-east-2 \
  --filters "Name=instance-state-name,Values=running" \
  --query "Reservations[].Instances[].[InstanceId,InstanceType]" \
  --output table

# Apply the hop limit fix (replace with actual instance ID)
aws ec2 modify-instance-metadata-options \
  --instance-id <YOUR_INSTANCE_ID> \
  --http-put-response-hop-limit 2 \
  --http-endpoint enabled \
  --region us-east-2
```

> **Note:** This command must be re-run every time a new EC2 node is created (i.e. every time you run `start.sh`). The instance ID changes each time. See [Permanent Fix](#permanent-fix-for-imds-hop-limit) below.

**Verify fix worked:**
```bash
aws eks describe-addon \
  --cluster-name smartfinvo \
  --addon-name aws-ebs-csi-driver \
  --region us-east-2 \
  --query "addon.status" \
  --output text
# Expected: ACTIVE
```

### Permanent Fix for IMDS Hop Limit

To avoid running this manually every time, add it to `terraform/eks.tf` in the node group launch template (future improvement):
```hcl
launch_template {
  metadata_options {
    http_put_response_hop_limit = 2
    http_endpoint               = "enabled"
  }
}
```

---

## Issue 6 — Redis Pod Stuck in Pending (Too Many Pods)

**Problem:** `t3.small` node hit its 11-pod limit. Redis pod could not be scheduled — stuck in `Pending` forever.

**Root cause found with:**
```bash
# Check why the pod is pending
kubectl describe pod -n smartfinvo -l app=redis
# Error: 0/1 nodes are available: 1 Too many pods

# Count all pods on the node
kubectl get pods -A \
  --field-selector spec.nodeName=<node-name>
# node-name found from: kubectl get nodes
```

**Fix — Scale down system deployments that had unnecessary 2 replicas:**
```bash
# ebs-csi-controller: 2 replicas not needed on single dev node
kubectl scale deployment ebs-csi-controller -n kube-system --replicas=1

# metrics-server: 2 replicas not needed on single dev node
kubectl scale deployment metrics-server -n kube-system --replicas=1
```

**Why it is safe:** These are monitoring/storage tools, not the app. For a single dev node, 1 replica is sufficient. In production you would keep 2 for high availability.

---

## Issue 7 — App Stuck at 1/2 Replicas (HPA Overriding)

**Problem:** App deployment had `replicas: 1` but HPA had `minReplicas: 2`. HPA kept forcing a second pod which got stuck in `Pending` — no room on the node.

**Root cause found with:**
```bash
kubectl get hpa smartfinvo-hpa -n smartfinvo
# Showed MINPODS: 2, which overrides deployment replicas
```

**Fix — Updated `k8s/app/hpa.yaml`:**
```yaml
# Before
minReplicas: 2
maxReplicas: 5

# After (fits t3.small single node)
minReplicas: 1
maxReplicas: 2
```

```bash
# Apply the updated HPA
kubectl apply -f k8s/app/hpa.yaml

# Force scale down immediately
kubectl scale deployment smartfinvo -n smartfinvo --replicas=1
```

---

## Health Check Commands

```bash
# App health (all components)
curl http://<LOAD_BALANCER_URL>/actuator/health

# Expected response:
# {
#   "status": "UP",
#   "components": {
#     "db":            { "status": "UP" },   ← PostgreSQL
#     "r2dbc":         { "status": "UP" },   ← Reactive DB driver
#     "redis":         { "status": "UP" },   ← Redis
#     "livenessState": { "status": "UP" },   ← App alive
#     "readinessState":{ "status": "UP" }    ← App ready for traffic
#   }
# }

# Swagger UI
open http://<LOAD_BALANCER_URL>/swagger-ui.html

# Get current Load Balancer URL
kubectl get svc smartfinvo-service -n smartfinvo
```

---

## Current Load Balancer URL

```
http://acd2d60b89fef402f831dab4fc60643f-888712003.us-east-2.elb.amazonaws.com
```

> **Note:** This URL changes every time the cluster is destroyed and recreated. Run `kubectl get svc smartfinvo-service -n smartfinvo` to get the current URL after each `./start.sh` + `./deploy.sh`.
