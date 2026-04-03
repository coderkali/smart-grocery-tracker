# Kubernetes Manifests

All Kubernetes manifests for the SmartFinvo platform.

## Structure

```
kubernetes/
├── base/                    # Core manifests (applied to all environments)
│   ├── namespace.yaml       # smartfinvo namespace
│   ├── configmap.yaml       # App configuration
│   ├── deployment.yaml      # Spring Boot app deployment
│   ├── service.yaml         # LoadBalancer service
│   ├── hpa.yaml             # Horizontal Pod Autoscaler
│   ├── postgres/            # PostgreSQL database
│   └── redis/               # Redis cache
└── overlays/                # Environment-specific patches (future use)
    ├── dev/
    ├── staging/
    └── production/
```

## Applying Manifests

```bash
# Apply all base manifests
kubectl apply -f kubernetes/base/

# Apply single component
kubectl apply -f kubernetes/base/deployment.yaml
```

## Important Notes

- `secret.yaml` is NOT stored in git (contains real credentials)
- Create your own secret: `kubectl create secret generic smartfinvo-secret --from-env-file=.env`
- See `scripts/deploy/deploy.sh` for full deployment flow