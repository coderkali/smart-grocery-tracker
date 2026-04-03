# Docker

Docker configuration files for the SmartFinvo platform.

## Files

| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build (deps → builder → runtime) |
| `postgres-init.sql` | PostgreSQL database initialization script |

## Building the Image

```bash
# From project root
docker build -f docker/Dockerfile -t smartfinvo:latest .

# Or using Make
make docker-build
```

## Key Design Decisions

- **3-stage multi-stage build** for minimal image size
- **Non-root user** (`appuser`) for security
- **JVM tuning** via `JAVA_OPTS` for container memory limits
- **Health check** uses `/actuator/health/liveness`

## Local Development

See `docker-compose.yml` in project root for local dev with PostgreSQL and Redis.