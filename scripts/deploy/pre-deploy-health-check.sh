#!/bin/bash

echo "=== PRE-DEPLOY HEALTH CHECK ==="

# Set variables
CLUSTER_NAME="smartfinvo"
REGION="us-east-2"

echo "Checking EKS cluster: $CLUSTER_NAME"

# Check 1: Does cluster exist?
echo "Check 1: Cluster exists?"
aws eks describe-cluster \
  --name $CLUSTER_NAME \
  --region $REGION \
  --query 'cluster.status' \
  --output text > /tmp/cluster_status.txt

CLUSTER_STATUS=$(cat /tmp/cluster_status.txt)

if [ -z "$CLUSTER_STATUS" ]; then
  echo "❌ FAILED: Cluster not found"
  exit 1
fi

echo "✅ Cluster found: $CLUSTER_STATUS"

# Check 2: Is cluster ACTIVE?
if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "❌ FAILED: Cluster status is $CLUSTER_STATUS (not ACTIVE)"
  exit 1
fi

echo "✅ Cluster is ACTIVE"

# Check 3: Are nodes ready?
echo "Check 2: Nodes ready?"

READY_NODES=$(kubectl get nodes \
  -o jsonpath='{.items[?(@.status.conditions[?(@.type=="Ready")].status=="True")].metadata.name}' | wc -w)

TOTAL_NODES=$(kubectl get nodes -o jsonpath='{.items[*].metadata.name}' | wc -w)

echo "Ready nodes: $READY_NODES / $TOTAL_NODES"

if [ $READY_NODES -lt 1 ]; then
  echo "❌ FAILED: No ready nodes"
  exit 1
fi

echo "✅ At least one node is ready"

# Check 4: Can we access the cluster?
echo "Check 3: Can access cluster?"

kubectl cluster-info > /dev/null 2>&1

if [ $? -ne 0 ]; then
  echo "❌ FAILED: Cannot access cluster"
  exit 1
fi

echo "✅ Cluster accessible"

echo ""
echo "=== ALL CHECKS PASSED ==="
echo "Safe to deploy!"
exit 0