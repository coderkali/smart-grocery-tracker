#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Start Script
# Usage: ./start.sh
#
# Run this at the START of your work session.
#
# Creates ONLY the cluster resources (not budget/IAM/WAF):
#   - EKS cluster (control plane)
#   - EKS node group (t3.small × 1)
#   - EKS add-ons (coredns, kube-proxy, vpc-cni, metrics-server)
#
# Time to start: ~10-12 minutes
# Cost while running: ~$0.15/hour
# ══════════════════════════════════════════════════════════════════

set -e

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║        SmartFinvo — Starting Up          ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "⏱  Estimated time: 10-12 minutes"
echo "💰 Cost while running: ~\$0.15/hour"
echo ""

cd "$(dirname "$0")/terraform"

# ── Notify: session started ────────────────────────────────────────
SNS_ARN=$(terraform output -raw billing_alert_sns_arn 2>/dev/null || echo "")
if [ -n "$SNS_ARN" ]; then
  aws sns publish \
    --topic-arn "$SNS_ARN" \
    --subject "SmartFinvo — Cluster Starting" \
    --message "Your SmartFinvo EKS cluster is being started.

Time:   $(date)
Action: terraform apply (cluster only)

If this was not you, check your AWS credentials immediately." \
    --region us-east-2 > /dev/null
fi

# ── Step 1: Create cluster resources only ─────────────────────────
echo "▶ Step 1/3 — Creating EKS cluster + node..."
terraform apply \
  -target=aws_eks_cluster.smartfinvo \
  -target=aws_eks_addon.coredns \
  -target=aws_eks_addon.kube_proxy \
  -target=aws_eks_addon.vpc_cni \
  -target=aws_eks_addon.pod_identity \
  -target=aws_eks_addon.metrics_server \
  -target=aws_eks_addon.ebs_csi_driver \
  -target=aws_eks_node_group.smartfinvo \
  -auto-approve

# ── Step 2: Connect kubectl ────────────────────────────────────────
echo ""
echo "▶ Step 2/3 — Connecting kubectl to cluster..."
aws eks update-kubeconfig \
  --region us-east-2 \
  --name smartfinvo

# ── Step 3: Wait for node to be Ready ─────────────────────────────
echo ""
echo "▶ Step 3/3 — Waiting for node to be Ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=300s

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║         ✅  SmartFinvo is UP!            ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "📦 Nodes:"
kubectl get nodes -o wide
echo ""
echo "⚠️  Remember to run ./stop.sh when you're done!"
echo ""

# ── Notify: cluster is up ──────────────────────────────────────────
if [ -n "$SNS_ARN" ]; then
  aws sns publish \
    --topic-arn "$SNS_ARN" \
    --subject "SmartFinvo — Cluster is UP ✅" \
    --message "Your SmartFinvo EKS cluster is now running.

Time:    $(date)
Node:    t3.small (1 node)
Cost:    ~\$0.15/hour while running

Remember to run ./stop.sh when you are done to avoid charges." \
    --region us-east-2 > /dev/null
fi
