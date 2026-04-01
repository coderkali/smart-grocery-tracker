<div align="center">

# 🛒 SmartFinvo — Smart Grocery Tracker

### AI-Powered Grocery & Expense Tracking Platform

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-EKS-326CE5?style=flat-square&logo=kubernetes)](https://kubernetes.io/)
[![Terraform](https://img.shields.io/badge/Terraform-IaC-7B42BC?style=flat-square&logo=terraform)](https://www.terraform.io/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4o-412991?style=flat-square&logo=openai)](https://openai.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

> Track groceries. Analyse spending. Chat with AI. All in one reactive backend.

</div>

---

## 📌 Table of Contents

- [Overview](#-overview)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Local Setup](#-local-setup)
- [AWS Deployment](#-aws-deployment)
- [Daily AWS Workflow](#-daily-aws-workflow-start--stop)
- [API Reference](#-api-reference)
- [Cost Breakdown](#-cost-breakdown)
- [Security & Monitoring](#-security--monitoring)
- [Future Enhancements](#-future-enhancements)

---

## 🧠 Overview

**SmartFinvo** is a production-grade, AI-powered backend platform that helps users track groceries, manage expenses by category, and get intelligent spending insights — all through a fully reactive REST API.

### Business Problems it Solves

| Problem | SmartFinvo Solution |
|---|---|
| Manual grocery list management | Natural language AI parses free-text into structured items |
| No visibility into spending habits | Expense tracker with category breakdown and date filtering |
| Forgetting regularly bought items | RAG-powered smart suggestions based on purchase history |
| Recipe planning is time-consuming | Conversational AI remembers your chat context across turns |
| Overspending without realising | Budget analysis with GPT-4o verified against real data |

### Key Highlights

- ⚡ **Fully reactive** — built on Spring WebFlux (non-blocking, high concurrency)
- 🤖 **AI-first** — GPT-4o for NLP, pgvector for RAG, Redis for chat memory
- 🔐 **Production auth** — Google OAuth2 + JWT rotation + session management
- ☁️ **Cloud-native** — Dockerised, Kubernetes-ready, deployed on AWS EKS
- 🛡️ **Cost-safe** — WAF, budget alerts, IAM guardrails, EventBridge monitoring

---

## 🛠 Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 | Latest LTS — virtual threads ready |
| **Framework** | Spring Boot 3.2 (WebFlux) | Reactive, non-blocking API |
| **Architecture** | Modular Monolith | Microservice-ready module boundaries |
| **Database** | PostgreSQL 15 + pgvector | Relational data + vector embeddings for RAG |
| **Migrations** | Flyway 10 | Schema versioning per module |
| **Cache** | Redis 7 | Session store + AI chat memory |
| **Auth** | Spring Security + Google OAuth2 + JWT HS512 | Secure token rotation |
| **AI** | Spring AI + OpenAI GPT-4o | NLP, suggestions, recipe chat, budget analysis |
| **API Docs** | SpringDoc / OpenAPI 3 | Swagger UI at `/swagger-ui.html` |
| **Build** | Maven + multi-stage Docker | Reproducible, minimal image |
| **CI** | GitHub Actions | Automated build and test |
| **Orchestration** | Kubernetes (AWS EKS) | Container management |
| **Infrastructure** | Terraform | Infrastructure as Code (AWS EKS, IAM, WAF) |
| **Security** | AWS WAF + EventBridge + IAM Guardrails | DDoS, cost abuse, account protection |

---

## 🏗 Architecture

### Design Pattern — Modular Monolith

Each module owns its domain completely — its own API port, application service, domain model, and infrastructure layer. Modules **never import each other's internals**. They communicate only through defined port interfaces.

This means any module can be extracted into a standalone microservice with **zero code changes** — just point it at its own database.

```
┌─────────────────────────────────────────────────────────────┐
│                     SmartFinvo Application                  │
│                                                             │
│  ┌──────────┐   ┌──────────────┐   ┌────────────────────┐  │
│  │   Auth   │   │   Expense    │   │        AI          │  │
│  │  Module  │   │   Module     │   │      Module        │  │
│  │          │   │              │   │                    │  │
│  │ OAuth2   │   │ Expenses     │   │ NLP Search         │  │
│  │ JWT      │   │ Categories   │   │ Suggestions (RAG)  │  │
│  │ Sessions │   │ Filtering    │   │ Recipe Chat        │  │
│  └──────────┘   └──────────────┘   │ Budget Analysis    │  │
│                                    └────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
         │                │                    │
    PostgreSQL          Redis              OpenAI API
    + pgvector       (Sessions +          (GPT-4o +
                     Chat Memory)       Embeddings)
```

### Data Flow Diagram

```mermaid
graph TD
    User([👤 User]) -->|HTTPS| LB[AWS Load Balancer]
    LB --> WAF[AWS WAF\nRate Limit + DDoS]
    WAF --> App[Spring Boot App\nWebFlux Reactive]

    App -->|OAuth2 Login| Google[Google OAuth2]
    Google -->|JWT issued| App

    App -->|Read/Write| PG[(PostgreSQL 15\n+ pgvector)]
    App -->|Sessions\nChat Memory| Redis[(Redis 7)]
    App -->|GPT-4o\nEmbeddings| OpenAI[OpenAI API]

    subgraph AWS EKS
        App
    end

    subgraph AWS Account Protection
        Budget[AWS Budgets\n$100 limit] -->|Alert| SNS[SNS Topic]
        EventBridge[EventBridge\nRules] -->|Any resource change| SNS
        SNS -->|Email| Dev([📧 Developer])
        IAM[IAM Guardrails\nDeny expensive services]
    end

    subgraph Terraform IaC
        TF[terraform apply] -->|Creates| AWS EKS
        TF -->|Creates| AWS Account Protection
    end
```

### Request Flow

```
User Request
    │
    ▼
AWS Load Balancer  ──→  AWS WAF (rate limit 1000 req/5min per IP)
    │
    ▼
JwtAuthenticationFilter  ──→  validates Bearer token
    │
    ▼
Controller  ──→  Service  ──→  Repository  ──→  PostgreSQL
                   │
                   ├──→  Redis  (sessions, chat memory)
                   │
                   └──→  OpenAI  (AI features only)
```

---

## 📁 Project Structure

```
smart-grocery-tracker/
│
├── src/main/java/com/smartfinvo/
│   ├── SmartFinvoApplication.java        # Entry point
│   ├── SpringConfig.java                 # Global Spring config
│   ├── config/
│   │   └── SwaggerConfig.java            # OpenAPI / Swagger setup
│   │
│   └── modules/
│       ├── auth/                         # Authentication module
│       │   ├── api/AuthModulePort.java   # Public interface (port)
│       │   ├── application/AuthService.java
│       │   ├── domain/                   # UserAccount, RefreshToken
│       │   └── infrastructure/
│       │       ├── web/                  # AuthController, SecurityConfig
│       │       ├── persistence/          # JPA repositories
│       │       └── cache/               # Redis token cache
│       │
│       ├── expense/                      # Expense tracking module
│       │   ├── application/             # ExpenseService, CategoryService
│       │   ├── domain/                  # Expense, ExpenseCategory
│       │   └── infrastructure/
│       │       ├── web/                 # ExpenseController, CategoryController
│       │       │   └── dto/             # Request/Response DTOs
│       │       └── persistence/
│       │
│       └── ai/                          # AI features module
│           ├── api/AiModulePort.java
│           ├── application/AiService.java
│           └── infrastructure/
│               ├── web/AiController.java
│               ├── config/              # AI config, prompts
│               ├── memory/              # Redis chat memory
│               ├── tools/               # GPT function tools
│               └── seeder/              # Data seeder
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/                    # Flyway migrations
│       ├── auth/    V1__create_auth_tables.sql
│       ├── user/    V2__create_user_tables.sql
│       ├── ai/      V3__ai_module_schema.sql
│       └── expense/ V4__create_expense_tables.sql
│
├── k8s/                                 # Kubernetes manifests
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── app/        deployment, service, hpa
│   ├── postgres/   deployment, service
│   └── redis/      deployment, service
│
├── terraform/                           # Infrastructure as Code
│   ├── main.tf                          # AWS provider config
│   ├── variables.tf                     # All configurable values
│   ├── eks.tf                           # EKS cluster + node group
│   ├── data.tf                          # Data sources (VPC, IAM roles)
│   ├── budget.tf                        # Cost alerts and guardrails
│   ├── security.tf                      # WAF + IAM deny policies
│   ├── notifications.tf                 # EventBridge email alerts
│   └── output.tf                        # Terraform outputs
│
├── setup.sh                             # ⚡ One-time AWS setup
├── start.sh                             # ▶️  Start AWS cluster
├── stop.sh                              # ⏹️  Stop AWS cluster (charges = $0)
├── Dockerfile                           # Multi-stage Docker build
├── docker-compose.yml                   # Local infra (postgres + redis)
└── Makefile                             # Convenience commands
```

---

## 💻 Local Setup

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21+ | [sdkman.io](https://sdkman.io) |
| Maven | 3.9+ | bundled (`./mvnw`) |
| Docker | 24+ | [docker.com](https://docker.com) |
| Docker Compose | 2+ | bundled with Docker |

### Step 1 — Clone the repo

```bash
git clone https://github.com/coderkali/smart-grocery-tracker
cd smart-grocery-tracker
```

### Step 2 — Configure environment

```bash
cp .env.example .env
```

Edit `.env` and fill in your values:

```env
# Google OAuth2 — get from console.cloud.google.com
OAUTH2_GOOGLE_CLIENT_ID=your-google-client-id
OAUTH2_GOOGLE_CLIENT_SECRET=your-google-client-secret

# OpenAI — get from platform.openai.com
OPENAI_API_KEY=sk-your-key

# JWT — must be at least 64 characters
APP_JWT_SECRET=your-super-secret-jwt-key-minimum-64-characters-long
```

### Step 3 — Start infrastructure (PostgreSQL + Redis)

```bash
make up-infra
# or
docker-compose up -d postgres redis
```

### Step 4 — Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway migrations run automatically on startup.

### Step 5 — Open Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### Stop Local Services

```bash
docker-compose down        # stop postgres + redis
# or
docker-compose down -v     # stop + delete all data
```

---

## ☁️ AWS Deployment

### How It Works

```
Your Mac
   │
   ├── ./setup.sh   → creates permanent AWS account protections (once ever)
   ├── ./start.sh   → creates EKS cluster + node (~10 min)
   └── ./stop.sh    → deletes cluster, charges drop to $0 (~5 min)
```

The app runs on **AWS EKS** (Elastic Kubernetes Service):

| AWS Service | What it does |
|---|---|
| **EKS** | Runs the Kubernetes control plane |
| **EC2 t3.small** | Worker node where pods run |
| **ECR** | Docker image registry |
| **Load Balancer** | Routes traffic to pods |
| **WAF** | Rate limiting + DDoS protection |
| **AWS Budgets** | $100/month limit with email alerts |
| **EventBridge** | Emails you on any resource change |
| **IAM** | Deny policy blocks expensive services |
| **SNS** | Email notification channel |

### Prerequisites

```bash
# Install required tools
brew install awscli terraform kubectl

# Configure AWS credentials
aws configure
# Enter: Access Key, Secret Key, Region (us-east-2), output (json)
```

### First Time Only — One-Time Setup

```bash
chmod +x setup.sh start.sh stop.sh
./setup.sh
```

This creates permanent protections (Budget, WAF, IAM, SNS) that **never get deleted** by `./stop.sh`.

> ⚠️ After running `setup.sh`, check `coderkali@gmail.com` for an AWS confirmation email and click **Confirm subscription** to activate email alerts.

---

## 🔁 Daily AWS Workflow (Start / Stop)

### ▶️ Start — Beginning of Work Session

```bash
./start.sh
```

**What happens:**
1. Creates EKS cluster (control plane)
2. Creates t3.small worker node
3. Installs Kubernetes add-ons (CoreDNS, kube-proxy, metrics-server)
4. Connects `kubectl` to the cluster
5. Sends you a startup email notification

⏱ Takes ~10–12 minutes. Cost while running: **~$0.15/hour**

**Verify the cluster is up:**
```bash
kubectl get nodes         # should show 1 node Ready
kubectl get pods -A       # should show system pods Running
```

### ⏹️ Stop — End of Work Session

```bash
./stop.sh
```

**What happens:**
1. Deletes EKS node group (stops EC2 charges)
2. Deletes EKS add-ons
3. Deletes EKS cluster (stops $0.10/hr control plane charge)
4. Sends you a shutdown email notification
5. Budget alerts, WAF, IAM guardrails **remain active**

⏱ Takes ~5–8 minutes. Cost after stopping: **$0.00**

> ⚠️ Always run `./stop.sh` at the end of your session. Leaving it running overnight = ~$3.60 in charges.

### Verify Everything is Deleted (AWS Console)

| Service | Where to check | Expected |
|---|---|---|
| EKS | Console → EKS → Clusters | No clusters listed |
| EC2 | Console → EC2 → Instances | No running instances |
| Load Balancer | Console → EC2 → Load Balancers | None |

---

## 📡 API Reference

### Authentication

> Login is done via Google OAuth2 — redirect the user to:
> `GET /oauth2/authorization/google`
> On success, you receive a JWT access token + refresh token cookie.
> Pass the token as: `Authorization: Bearer <token>`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/oauth2/authorization/google` | Initiate Google login |
| `POST` | `/api/v1/auth/refresh` | Rotate tokens (uses cookie) |
| `POST` | `/api/v1/auth/logout` | Logout current device |
| `POST` | `/api/v1/auth/logout/all` | Logout all devices |
| `GET` | `/api/v1/auth/me` | Get current user profile |
| `GET` | `/api/v1/auth/sessions` | List active sessions |
| `DELETE` | `/api/v1/auth/sessions/{id}` | Revoke a session |

### Expenses

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/expenses` | Create expense |
| `GET` | `/api/v1/expenses` | List all expenses |
| `GET` | `/api/v1/expenses/{id}` | Get by ID |
| `GET` | `/api/v1/expenses?start_date=&end_date=` | Filter by date range |
| `GET` | `/api/v1/expenses?category_id=` | Filter by category |
| `PUT` | `/api/v1/expenses/{id}` | Update expense |
| `DELETE` | `/api/v1/expenses/{id}` | Soft-delete expense |

**Sample Request — Create Expense:**
```json
POST /api/v1/expenses
{
  "category_id": "22222222-2222-2222-2222-222222222222",
  "amount": 45.99,
  "currency": "USD",
  "description": "Weekly grocery run",
  "expense_date": "2026-03-28",
  "payment_method": "credit_card",
  "tags": "groceries,weekly"
}
```

### Categories

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/categories` | Create category |
| `GET` | `/api/v1/categories` | List active categories |
| `GET` | `/api/v1/categories/{id}` | Get by ID |
| `PUT` | `/api/v1/categories/{id}` | Update category |
| `DELETE` | `/api/v1/categories/{id}` | Deactivate category |

**Sample Request — Create Category:**
```json
POST /api/v1/categories
{
  "name": "Groceries",
  "icon": "🛒",
  "color": "#4CAF50"
}
```

### AI Features

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/ai/search` | Natural language grocery management |
| `POST` | `/api/v1/ai/suggest` | Smart item suggestions (RAG) |
| `POST` | `/api/v1/ai/recipe` | Recipe assistant — streaming SSE |
| `POST` | `/api/v1/ai/budget` | Spending & budget analysis |

**Sample Request — Natural Language Search:**
```json
POST /api/v1/ai/search
{
  "message": "add 2L milk and a dozen eggs",
  "listId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

**Sample Response:**
```json
{
  "aiResponse": "Added 2 items to your list ✓",
  "itemsAdded": [
    { "name": "milk", "quantity": 2.0, "unit": "litre", "category": "dairy" },
    { "name": "eggs", "quantity": 12.0, "unit": "piece", "category": "dairy" }
  ],
  "tokensUsed": 187
}
```

> 📖 Full interactive docs at `http://localhost:8080/swagger-ui.html`

---

## 💰 Cost Breakdown

### Daily Usage (2 hours/day)

```
2 hrs/day × 30 days = 60 hours/month

EKS control plane:  $0.10/hr  × 60hrs = $ 6.00
EC2 t3.small:       $0.023/hr × 60hrs = $ 1.38
Load Balancer:      $0.025/hr × 60hrs = $ 1.50
EBS disk (20GB):                       = $ 0.30
                                       ────────
Monthly total                          = ~$9.18
```

### If Left Running 24/7 (worst case)

```
EKS control plane:  $72.00/month
EC2 t3.small:       $16.90/month
Load Balancer:      $18.00/month
EBS disk:           $ 2.00/month
                   ────────────
Worst case         ~$108/month
```

> 💡 **Always run `./stop.sh` at end of session.** Budget alert fires at $80, IAM locks down at $100.

---

## 🛡️ Security & Monitoring

### Account Protection Layers

| Layer | What it does | Triggers at |
|---|---|---|
| **AWS WAF** | Blocks known bad IPs, rate limits 1000 req/5min, blocks SQLi/XSS | Every request |
| **Budget Alert — Warning** | Email to `coderkali@gmail.com` | $80 spent |
| **Budget Alert — Limit** | Email alert | $100 spent |
| **IAM Deny Policy** | Auto-attached — blocks RDS, SageMaker, large instances, new IAM users | $100 spent |
| **EventBridge — EKS** | Email when cluster is created or deleted by anyone | Instantly |
| **EventBridge — EC2** | Email when any instance is launched or terminated | Instantly |
| **EventBridge — Expensive** | Security alert if RDS/SageMaker/Redshift is created | Instantly |

### IAM Guardrails (applied at $100)

- ❌ No instance types larger than `t2.*` / `t3.*`
- ❌ No resources outside `us-east-2`
- ❌ No RDS, Redshift, ElastiCache, SageMaker, Bedrock
- ❌ No new IAM users or roles (blocks privilege escalation)
- ❌ No data exfiltration services (Glacier, DataSync, Snowball)

### Application Security

- JWT access tokens expire in **15 minutes**
- Refresh tokens rotate on every use (one-time use)
- Refresh token stored as `HttpOnly` cookie (not accessible via JavaScript)
- All sessions visible and revocable per device

---

## 🚀 Future Enhancements

| # | Enhancement | Description |
|---|---|---|
| 1 | **Push Notifications** | Real-time alerts when budget thresholds are hit in-app |
| 2 | **Receipt OCR** | Upload a receipt photo → AI extracts items and amounts automatically |
| 3 | **Multi-currency Support** | Auto-convert expenses to a base currency using live exchange rates |
| 4 | **Shared Lists** | Invite family members to a shared grocery list with real-time sync |
| 5 | **Microservice Extraction** | Extract AI module to a standalone service — module boundaries are already clean |

---

<div align="center">

Built with ☕ Java, 🤖 GPT-4o, and ☁️ AWS

</div>
# Pipeline test
