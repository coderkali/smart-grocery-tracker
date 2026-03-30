#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Stop Script
# Usage: ./stop.sh
#
# Run this at the END of your work session.
#
# Destroys ONLY the cluster resources — Budget/IAM/WAF stay alive:
#   - EKS node group  → deleted (stops EC2 charges)
#   - EKS add-ons     → deleted
#   - EKS cluster     → deleted (stops $0.10/hr control plane charge)
#
# What stays alive (account protections):
#   - Budget alerts   → still active ✅
#   - IAM guardrails  → still active ✅
#   - WAF             → still active ✅
#   - SNS topic       → still active ✅
#
# Time to stop: ~5-8 minutes
# Cost after stop: $0.00
# ══════════════════════════════════════════════════════════════════

set -e

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║        SmartFinvo — Shutting Down        ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "⏱  Estimated time: 7-10 minutes"
echo "💰 Cost after shutdown: \$0.00"
echo ""
echo "ℹ️  Budget alerts, IAM guardrails and WAF will stay active."
echo ""

# Safety confirmation
read -p "⚠️  This will DELETE the EKS cluster and nodes. Are you sure? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
  echo "❌ Cancelled — nothing was deleted."
  exit 0
fi

SCRIPT_DIR="$(dirname "$0")"

# ── Step 1: Delete Kubernetes resources ───────────────────────────
# Must happen BEFORE terraform destroy.
# K8s resources create AWS Load Balancer + EBS volume behind the scenes.
# If we destroy the cluster first, those AWS resources become orphaned
# (dangling, still billing you, and blocking VPC deletion).
echo ""
echo "▶ Step 1/2 — Deleting Kubernetes resources (Load Balancer + EBS)..."
echo ""

if kubectl get namespace smartfinvo &>/dev/null; then
  kubectl delete -f "$SCRIPT_DIR/k8s/app/"      --ignore-not-found=true
  kubectl delete -f "$SCRIPT_DIR/k8s/postgres/"  --ignore-not-found=true
  kubectl delete -f "$SCRIPT_DIR/k8s/redis/"     --ignore-not-found=true
  kubectl delete -f "$SCRIPT_DIR/k8s/configmap.yaml" --ignore-not-found=true
  kubectl delete -f "$SCRIPT_DIR/k8s/secret.yaml"    --ignore-not-found=true
  kubectl delete namespace smartfinvo --ignore-not-found=true
  echo ""
  echo "   Waiting 60s for AWS Load Balancer to be fully removed..."
  sleep 60
else
  echo "   Namespace smartfinvo not found — skipping kubectl delete."
fi

# ── Step 2: Delete EKS cluster ────────────────────────────────────
echo ""
echo "▶ Step 2/2 — Deleting EKS cluster and nodes..."

cd "$SCRIPT_DIR/terraform"

# ── Notify: shutdown started ───────────────────────────────────────
SNS_ARN=$(terraform output -raw billing_alert_sns_arn 2>/dev/null || echo "")
if [ -n "$SNS_ARN" ]; then
  aws sns publish \
    --topic-arn "$SNS_ARN" \
    --subject "SmartFinvo — Cluster Shutting Down" \
    --message "Your SmartFinvo EKS cluster is being shut down.

Time:   $(date)
Action: terraform destroy (cluster only)

Budget alerts, IAM guardrails and WAF remain active." \
    --region us-east-2 > /dev/null
fi

terraform destroy \
  -target=aws_eks_node_group.smartfinvo \
  -target=aws_eks_addon.coredns \
  -target=aws_eks_addon.kube_proxy \
  -target=aws_eks_addon.vpc_cni \
  -target=aws_eks_addon.pod_identity \
  -target=aws_eks_addon.metrics_server \
  -target=aws_eks_addon.ebs_csi_driver \
  -target=aws_eks_cluster.smartfinvo \
  -auto-approve

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║       ✅  Cluster deleted!               ║"
echo "║          AWS charges: \$0.00             ║"
echo "║                                          ║"
echo "║  Budget + IAM + WAF still protecting     ║"
echo "║  your account 24/7  ✅                   ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "Run ./start.sh when you want to start again (~10 min)."
echo ""

# ── Notify: shutdown complete ──────────────────────────────────────
if [ -n "$SNS_ARN" ]; then
  aws sns publish \
    --topic-arn "$SNS_ARN" \
    --subject "SmartFinvo — Cluster Deleted ✅ Charges Stopped" \
    --message "Your SmartFinvo EKS cluster has been fully deleted.

Time:    $(date)
Charges: \$0.00 (nothing running)

Budget alerts, IAM guardrails and WAF are still active.
Run ./start.sh to bring the cluster back up (~10 min)." \
    --region us-east-2 > /dev/null
fi
