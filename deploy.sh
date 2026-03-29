#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Deploy Script
# Usage: ./deploy.sh
#
# Deploys the full application stack to EKS:
#   1. Namespace
#   2. Secrets + ConfigMap
#   3. PostgreSQL (database)
#   4. Redis (cache)
#   5. SmartFinvo App (Spring Boot)
#
# Run AFTER start.sh has finished.
# ══════════════════════════════════════════════════════════════════

set -e

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║      SmartFinvo — Deploying App          ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# ── Step 1: ECR Login ──────────────────────────────────────────────
echo "▶ Step 1/6 — Logging into ECR..."
aws ecr get-login-password --region us-east-2 \
  | docker login \
    --username AWS \
    --password-stdin \
    274214919013.dkr.ecr.us-east-2.amazonaws.com
echo "✅ ECR login successful"
echo ""

# ── Step 2: Namespace ──────────────────────────────────────────────
echo "▶ Step 2/6 — Creating namespace..."
kubectl apply -f k8s/namespace.yaml
echo ""

# ── Step 3: Secrets + ConfigMap ────────────────────────────────────
echo "▶ Step 3/6 — Applying secrets and config..."
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
echo ""

# ── Step 4: PostgreSQL ─────────────────────────────────────────────
echo "▶ Step 4/6 — Deploying PostgreSQL..."
kubectl apply -f k8s/postgres/deployment.yaml
kubectl apply -f k8s/postgres/service.yaml

echo "  Waiting for PostgreSQL to be ready..."
kubectl rollout status deployment/postgres -n smartfinvo --timeout=180s
echo "✅ PostgreSQL is ready"
echo ""

# ── Step 5: Redis ──────────────────────────────────────────────────
echo "▶ Step 5/6 — Deploying Redis..."
kubectl apply -f k8s/redis/deployment.yaml
kubectl apply -f k8s/redis/service.yaml

echo "  Waiting for Redis to be ready..."
kubectl rollout status deployment/redis -n smartfinvo --timeout=120s
echo "✅ Redis is ready"
echo ""

# ── Step 6: App ────────────────────────────────────────────────────
echo "▶ Step 6/6 — Deploying SmartFinvo app..."
kubectl apply -f k8s/app/deployment.yaml
kubectl apply -f k8s/app/service.yaml
kubectl apply -f k8s/app/hpa.yaml

echo "  Waiting for app to be ready (may take 2-3 min)..."
kubectl rollout status deployment/smartfinvo -n smartfinvo --timeout=300s
echo "✅ App is ready"
echo ""

# ── Get Load Balancer URL ──────────────────────────────────────────
echo "⏳ Fetching Load Balancer URL (takes ~1 min after first deploy)..."
for i in $(seq 1 12); do
  LB_URL=$(kubectl get svc smartfinvo-service -n smartfinvo \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
  if [ -n "$LB_URL" ]; then
    break
  fi
  echo "  Waiting for Load Balancer... ($i/12)"
  sleep 10
done

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║         ✅  SmartFinvo is LIVE!          ║"
echo "╚══════════════════════════════════════════╝"
echo ""

if [ -n "$LB_URL" ]; then
  echo "🌐 App URL:    http://$LB_URL"
  echo "📖 Swagger UI: http://$LB_URL/swagger-ui.html"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "Sample curl commands:"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
  echo "# Register a new user:"
  echo "curl -X POST http://$LB_URL/api/v1/auth/register \\"
  echo "  -H 'Content-Type: application/json' \\"
  echo "  -d '{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"Test@1234\"}'"
  echo ""
  echo "# Login:"
  echo "curl -X POST http://$LB_URL/api/v1/auth/login \\"
  echo "  -H 'Content-Type: application/json' \\"
  echo "  -d '{\"email\":\"test@example.com\",\"password\":\"Test@1234\"}'"
  echo ""
  echo "# Health check:"
  echo "curl http://$LB_URL/actuator/health"
  echo ""
else
  echo "⚠️  Load Balancer URL not ready yet. Run this to get it:"
  echo "   kubectl get svc smartfinvo-service -n smartfinvo"
fi

echo ""
kubectl get pods -n smartfinvo
echo ""
