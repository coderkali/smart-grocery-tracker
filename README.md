# 🛒 Smart Grocery Tracker

> AI-powered grocery & expense tracking platform — Backend API

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Architecture | Modular Monolith (microservice-ready) |
| Database | PostgreSQL 15 |
| Migrations | Flyway 10 |
| Cache / Sessions | Redis 7 |
| Auth | Spring Security + OAuth2 + JWT (HS512) |
| API Docs | SpringDoc / OpenAPI 3 |
| Build | Maven + multi-stage Docker |
| CI | GitHub Actions |

---

## Local Development — 5 Commands

```bash
# 1. Clone
git clone https://github.com/your-org/smart-grocery-tracker && cd smart-grocery-tracker

# 2. Copy env file and fill in OAuth2 keys
cp .env.example .env

# 3. Start infrastructure (postgres + redis)
make up-infra

# 4. Run the app (local profile, Flyway runs automatically)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 5. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

**Or start everything in Docker:**
```bash
make up
```

---

## Module Structure

```
src/main/java/com/smartgrocery/
├── SmartGroceryTrackerApplication.java
│
├── shared/                        # Cross-cutting concerns
│   ├── config/BaseEntity.java     # All entities extend this
│   ├── response/ApiResponse.java  # Standard response envelope
│   ├── exception/                 # Global exception handling
│   ├── security/                  # JWT + Spring Security config
│   └── audit/RequestContext.java  # Thread-local user context
│
└── modules/
    ├── auth/          # OAuth2, JWT, sessions, token rotation
    ├── user/          # User profiles, onboarding
    ├── expense/       # Transactions, categories, budgets
    ├── ai/            # OpenAI/Gemini chat integration
    ├── notification/  # Email alerts via SendGrid
    └── cardsync/      # Plaid credit card sync
```

**Module boundary rule:** Modules communicate via `ApplicationEvent` only.  
No module imports another module's internal classes.  
This discipline means any module can be extracted to its own microservice with zero code changes.

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/actuator/health` | Health check |
| GET | `/swagger-ui.html` | API documentation |
| GET | `/api/v1/auth/me` | Current user |
| POST | `/api/v1/auth/refresh` | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Revoke all sessions |
| GET | `/api/v1/expenses` | List expenses (paginated) |
| POST | `/api/v1/expenses` | Create expense |
| GET | `/api/v1/expenses/{id}` | Get expense |
| PUT | `/api/v1/expenses/{id}` | Update expense |
| DELETE | `/api/v1/expenses/{id}` | Soft-delete expense |
| GET | `/api/v1/expenses/stats/monthly` | Monthly stats + category breakdown |
| GET | `/api/v1/expenses/stats/merchants` | Top merchants by spend |

---

## Flyway Migrations

```
src/main/resources/db/migration/
├── system/       V1_0_0__create_system_tables.sql   (plan, feature_flag)
├── auth/         V1_0_0__create_auth_tables.sql     (user_account, user_identity, refresh_token)
├── expense/      V1_0_0__create_expense_tables.sql  (expense, budget, audit_log, user_activity)
├── user/
├── notification/
├── cardsync/
└── ai/
```

Migrations run automatically on startup. Never edit a committed migration — always add a new version.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/smart_grocery` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `grocery_user` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `grocery_secret` | DB password |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `JWT_SECRET` | — | Min 64 chars, **required** |
| `OAUTH2_GOOGLE_CLIENT_ID` | — | Google OAuth2 |
| `OAUTH2_GOOGLE_CLIENT_SECRET` | — | Google OAuth2 |
| `OAUTH2_GITHUB_CLIENT_ID` | — | GitHub OAuth2 |
| `OAUTH2_GITHUB_CLIENT_SECRET` | — | GitHub OAuth2 |
| `OPENAI_API_KEY` | — | AI chat feature |
| `PLAID_CLIENT_ID` | — | Card sync feature |

---

## Architecture Decision Records

See `/docs/adr/` for all architectural decisions.

- ADR-001: Modular Monolith
- ADR-002: Schema-per-Tenant
- ADR-003: JWT + Refresh Token Rotation
- ADR-004: Flyway Migrations
