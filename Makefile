# ══════════════════════════════════════════════════════════════════════════
# Smart Grocery Tracker — Developer Makefile
# Usage: make <target>
# ══════════════════════════════════════════════════════════════════════════

.PHONY: help up down logs test build clean db-shell redis-shell api-docs docker-build start stop deploy

# ── Help ──────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "  Smart Grocery Tracker — Available Commands"
	@echo "  ─────────────────────────────────────────"
	@echo "  make up          Start all Docker services"
	@echo "  make up-infra    Start only postgres + redis"
	@echo "  make down        Stop all services"
	@echo "  make down-clean  Stop and delete all volumes"
	@echo "  make logs        Tail app logs"
	@echo "  make test        Run tests"
	@echo "  make test-cov    Run tests + open coverage report"
	@echo "  make build       Build the JAR"
	@echo "  make db-shell    Open psql shell"
	@echo "  make redis-shell Open redis-cli"
	@echo "  make api-docs    Open Swagger UI"
	@echo "  make mailhog     Open MailHog web UI"
	@echo "  make pgadmin     Start pgAdmin (then open localhost:5050)"
	@echo "  make docker-build Build Docker image (docker/Dockerfile)"
	@echo "  make start       Start AWS EKS cluster"
	@echo "  make stop        Stop AWS EKS cluster"
	@echo "  make deploy      Deploy app to EKS"
	@echo ""

# ── Docker ────────────────────────────────────────────────────────────────
up:
	docker compose up -d
	@echo "✓ All services started"
	@echo "  API:      http://localhost:8080"
	@echo "  Swagger:  http://localhost:8080/swagger-ui.html"
	@echo "  MailHog:  http://localhost:8025"

up-infra:
	docker compose up -d postgres redis
	@echo "✓ Infrastructure started (postgres + redis)"

down:
	docker compose down

down-clean:
	docker compose down -v
	@echo "✓ All services stopped and volumes deleted"

logs:
	docker compose logs -f app

# ── Maven ─────────────────────────────────────────────────────────────────
build:
	./mvnw package -DskipTests -B

test:
	./mvnw verify -B

test-cov:
	./mvnw verify -B
	open target/site/jacoco/index.html || xdg-open target/site/jacoco/index.html

clean:
	./mvnw clean

# ── Dev shortcuts ─────────────────────────────────────────────────────────
db-shell:
	docker exec -it sgt-postgres psql -U grocery_user -d smart_grocery

redis-shell:
	docker exec -it sgt-redis redis-cli -a redis_secret

api-docs:
	open http://localhost:8080/swagger-ui.html || xdg-open http://localhost:8080/swagger-ui.html

mailhog:
	open http://localhost:8025 || xdg-open http://localhost:8025

pgadmin:
	docker compose --profile tools up -d pgadmin
	@echo "PgAdmin: http://localhost:5050  (admin@smartgrocery.local / admin)"

# ── Docker ─────────────────────────────────────────────────────────────────
docker-build:
	docker build -f docker/Dockerfile -t smartfinvo:latest .

# ── AWS Cluster ────────────────────────────────────────────────────────────
start:
	bash scripts/start/start-cluster.sh

stop:
	bash scripts/stop/stop-cluster.sh

deploy:
	bash scripts/deploy/deploy.sh
