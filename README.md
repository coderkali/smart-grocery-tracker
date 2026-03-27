# Smart Grocery Tracker

> AI-powered grocery & expense tracking platform — Backend API

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 (WebFlux — reactive) |
| Architecture | Modular Monolith (microservice-ready) |
| Database | PostgreSQL 15 |
| Migrations | Flyway 10 |
| Cache / Sessions | Redis 7 |
| Auth | Spring Security + Google OAuth2 + JWT (HS512) |
| AI | Spring AI + OpenAI GPT-4o + pgvector (RAG) |
| API Docs | SpringDoc / OpenAPI 3 (Swagger UI) |
| Build | Maven + multi-stage Docker |
| CI | GitHub Actions |
| Container Orchestration | Kubernetes (k8s manifests included) |
| Infrastructure as Code | Terraform (AWS EKS) |

---

## Local Development

```bash
# 1. Clone
git clone https://github.com/coderkali/smart-grocery-tracker && cd smart-grocery-tracker

# 2. Copy env file and fill in OAuth2 / API keys
cp .env.example .env

# 3. Start infrastructure (postgres + redis)
make up-infra

# 4. Run the app (local profile, Flyway migrations run automatically)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 5. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

**Or run everything in Docker:**
```bash
make up
```

---

## Module Structure

```
src/main/java/com/smartfinvo/
├── SmartFinvoApplication.java
├── SpringConfig.java
├── config/
│   └── SwaggerConfig.java
│
└── modules/
    ├── auth/          # Google OAuth2, JWT, refresh token rotation, session management
    ├── expense/       # Expense records, categories, filtering
    └── ai/            # Natural language grocery management, smart suggestions,
                       # recipe chat (streaming SSE), budget analysis (RAG + pgvector)
```

**Module boundary rule:** Modules communicate only through their `api/` port interfaces.
No module imports another module's internal classes — any module can be extracted to its own microservice with zero code changes.

---

## API Endpoints

### Authentication
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/oauth2/authorization/google` | — | Initiate Google OAuth2 login |
| POST | `/api/v1/auth/refresh` | Cookie | Rotate access + refresh tokens |
| POST | `/api/v1/auth/logout` | JWT | Logout current device |
| POST | `/api/v1/auth/logout/all` | JWT | Logout all devices |
| GET | `/api/v1/auth/me` | JWT | Get current user profile |
| GET | `/api/v1/auth/sessions` | JWT | List all active sessions |
| DELETE | `/api/v1/auth/sessions/{id}` | JWT | Revoke a specific session |

### Expenses
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/expenses` | JWT | Create a new expense |
| GET | `/api/v1/expenses` | JWT | List all expenses |
| GET | `/api/v1/expenses/{id}` | JWT | Get expense by ID |
| GET | `/api/v1/expenses?start_date=&end_date=` | JWT | Filter by date range |
| GET | `/api/v1/expenses?category_id=` | JWT | Filter by category |
| PUT | `/api/v1/expenses/{id}` | JWT | Update an expense |
| DELETE | `/api/v1/expenses/{id}` | JWT | Soft-delete an expense |

### Categories
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/categories` | JWT | Create a category |
| GET | `/api/v1/categories` | JWT | List all active categories |
| GET | `/api/v1/categories/{id}` | JWT | Get category by ID |
| PUT | `/api/v1/categories/{id}` | JWT | Update a category |
| DELETE | `/api/v1/categories/{id}` | JWT | Deactivate a category |

### AI Features
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/ai/search` | JWT | Natural language grocery management |
| POST | `/api/v1/ai/suggest` | JWT | Smart item suggestions (RAG) |
| POST | `/api/v1/ai/recipe` | JWT | Conversational recipe assistant (streaming SSE) |
| POST | `/api/v1/ai/budget` | JWT | Spending & budget analysis |

---

## Authentication Flow

1. Redirect user to `GET /oauth2/authorization/google`
2. On success, receive a **JWT access token** in the response body and a **refresh token** as an `HttpOnly` cookie
3. Pass the access token as `Authorization: Bearer <token>` on every protected request
4. When the access token expires (401), call `POST /api/v1/auth/refresh` — the cookie is sent automatically and new tokens are returned

---

## Flyway Migrations

```
src/main/resources/db/migration/
├── auth/     V1__create_auth_tables.sql      (user_account, user_identity, refresh_token)
├── user/     V2__create_user_tables.sql
├── ai/       V3__ai_module_schema.sql         (ai_conversations, pgvector embeddings)
└── expense/  V4__create_expense_tables.sql    (expense, expense_category)
```

Migrations run automatically on startup. Never edit a committed migration — always add a new version.

---

## Environment Variables

| Variable | Description |
|---|---|
| `SPRING_R2DBC_URL` | PostgreSQL R2DBC connection URL |
| `SPRING_R2DBC_USERNAME` | DB username |
| `SPRING_R2DBC_PASSWORD` | DB password |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `JWT_SECRET` | Min 64 chars, **required** |
| `OAUTH2_GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `OAUTH2_GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `OPENAI_API_KEY` | OpenAI API key (AI features) |

Copy `.env.example` for a full list with descriptions.

---

## Deployment

### Kubernetes
Manifests are in `k8s/`:
```
k8s/
├── namespace.yaml
├── configmap.yaml
├── app/         # Deployment, Service, HPA
├── postgres/    # Deployment, Service
└── redis/       # Deployment, Service
```

### Terraform (AWS EKS)
Infrastructure scripts are in `terraform/`:
```bash
cd terraform
terraform init
terraform plan
terraform apply
```

---

## Swagger UI

Once the app is running, full interactive API docs are available at:

```
http://localhost:8080/swagger-ui.html
```
