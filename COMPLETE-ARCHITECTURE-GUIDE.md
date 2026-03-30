# SmartFinvo — Complete Architecture Guide
# For: Cloud Architects, Kubernetes Engineers, AWS Infrastructure Mentors
# Date: March 29, 2026
# Purpose: Enterprise-Level Understanding of Deployment Architecture

---

## TABLE OF CONTENTS

1. [Architecture Overview](#architecture-overview)
2. [AWS Architecture Deep Dive](#aws-architecture-deep-dive)
3. [Terraform Files Explained](#terraform-files-explained)
4. [Kubernetes Files Explained](#kubernetes-files-explained)
5. [Request Flow: User to Application](#request-flow)
6. [Architecture Diagrams](#diagrams)
7. [Learning Summary Table](#summary-table)

---

# ARCHITECTURE OVERVIEW

## What is SmartFinvo?

SmartFinvo is an **AI-powered household expense intelligence platform** deployed on AWS EKS (Elastic Kubernetes Service) with the following stack:

- **Frontend**: React (planned)
- **Backend**: Spring Boot 3.x + Java 21 (WebFlux - reactive)
- **AI/ML**: Spring AI + GPT-4o + pgvector (RAG)
- **Database**: PostgreSQL + pgvector
- **Cache**: Redis
- **Container Orchestration**: Kubernetes (EKS)
- **Cloud Provider**: AWS

## The Big Picture
```
User's Browser
    ↓
Load Balancer (ALB)
    ↓
WAF (Web Application Firewall)
    ↓
Kubernetes Cluster (EKS)
    ├─ Spring Boot App (2 replicas)
    ├─ PostgreSQL (stateful)
    └─ Redis (stateful cache)
    ↓
AWS RDS PostgreSQL
AWS ElastiCache Redis
```

---

# AWS ARCHITECTURE DEEP DIVE

## 1. VPC (Virtual Private Cloud)

**What it is**: Your own isolated network in AWS

**Why needed**: Security - your resources are private, not on the public internet

**SmartFinvo VPC**:
- VPC ID: `vpc-0b262b84f18196ea0`
- CIDR Block: `10.0.0.0/16` (65,536 IP addresses available)
- Region: `us-east-2` (Ohio)

**What lives in the VPC**:
- EKS Cluster
- EC2 worker nodes
- PostgreSQL RDS
- ElastiCache Redis
- NAT Gateway (for outbound traffic)

---

## 2. Subnets (Sub-networks)

**What they are**: Division of VPC into smaller networks for organization and high availability

**SmartFinvo Subnets**:

### Public Subnets (2)
- `10.0.0.0/20` (us-east-2a) - 4,096 IPs
- `10.0.16.0/20` (us-east-2b) - 4,096 IPs

**Purpose**: NAT Gateway lives here, ALB lives here
**Accessibility**: Can reach the internet directly

### Private Subnets (2)
- `10.0.128.0/20` (us-east-2a) - 4,096 IPs ← Worker nodes go here
- `10.0.144.0/20` (us-east-2b) - 4,096 IPs ← Worker nodes go here

**Purpose**: EKS nodes, RDS, Redis
**Accessibility**: Cannot reach internet directly (goes through NAT)

**Why 2 of each?**: High availability across 2 availability zones

---

## 3. Internet Gateway & NAT Gateway

### Internet Gateway (IGW)
- **Location**: Attached to public subnets
- **Purpose**: Route `0.0.0.0/0` (all internet traffic) from public subnets to the internet
- **Used by**: Load Balancer, users accessing your API

### NAT Gateway
- **Location**: In public subnet
- **Purpose**: Allows private subnet resources (pods, RDS) to reach the internet for:
  - Docker image pulls from ECR
  - OpenAI API calls
  - Software package downloads
- **Cost**: ~$32/month + data transfer costs
- **Availability**: 1 per region (deployed in us-east-2a)

---

## 4. Security Groups (Virtual Firewalls)

**What they are**: Inbound/outbound traffic rules

**SmartFinvo Security Groups**:

### ALB Security Group
- **Inbound**: 
  - Port 80 (HTTP) from 0.0.0.0/0
  - Port 443 (HTTPS) from 0.0.0.0/0
- **Outbound**: All to EKS security group

### EKS Security Group
- **Inbound**:
  - Port 8080 from ALB security group
  - Port 5432 (PostgreSQL) from self
  - Port 6379 (Redis) from self
- **Outbound**: All (to internet via NAT)

### RDS Security Group
- **Inbound**: Port 5432 from EKS security group only
- **Outbound**: None needed

### ElastiCache Security Group
- **Inbound**: Port 6379 from EKS security group only
- **Outbound**: None needed

---

## 5. EKS Cluster (Kubernetes Control Plane)

**What it is**: AWS-managed Kubernetes control plane

**SmartFinvo EKS**:
- **Cluster Name**: `smartfinvo`
- **Kubernetes Version**: 1.35
- **Control Plane**: Managed by AWS (you don't pay extra for this)
- **Location**: Public subnets (for kubectl access from your Mac)
- **API Endpoint**: `arn:aws:eks:us-east-2:274214919013:cluster/smartfinvo`

**What the control plane does**:
- Hosts the Kubernetes API server
- Manages the etcd database (cluster state)
- Runs the scheduler (decides which pod goes to which node)
- Runs controllers (health checks, replication, etc.)

**Cost**: Included in EKS pricing

---

## 6. Worker Nodes (EC2 Instances)

**What they are**: EC2 instances where your pods actually run

**SmartFinvo Nodes**:
- **Node Type**: t3.medium (2 vCPU, 4GB RAM)
- **Count**: 2 (desired), 1 (min), 5 (max for autoscaling)
- **Location**: Private subnets (10.0.128.0/20 and 10.0.144.0/20)
- **Cost**: ~$34/month per node × 2 = ~$68/month

**What runs on each node**:
- kubelet (talks to control plane)
- kube-proxy (networking)
- container runtime (Docker)
- Your application pods
- System pods (coredns, vpc-cni)

---

## 7. Load Balancer (ALB)

**What it is**: Amazon Application Load Balancer

**SmartFinvo ALB**:
- **Type**: Application Load Balancer (Layer 7 - understands HTTP)
- **Location**: Public subnets
- **Endpoint**: Provided by AWS (e.g., smartfinvo-alb-123456.us-east-2.elb.amazonaws.com)
- **Purpose**: Distribute traffic to pods across multiple nodes

**How it works**:
```
User Request → ALB (public subnet)
                  ↓
             WAF Rules Check
                  ↓
             Route to Node 1 or 2 (private subnet)
                  ↓
             Pod inside node processes request
```

**Cost**: ~$16/month + data transfer

---

## 8. WAF (Web Application Firewall)

**What it is**: Smart traffic filter protecting your application

**SmartFinvo WAF Rules**:

1. **IP Reputation List**: Blocks known malicious IPs, botnets, Tor nodes
2. **Rate Limiting**: Blocks IPs sending >1000 requests/5min → 403 error
3. **Common Exploits**: Blocks SQLi, XSS, path traversal attacks

**Protects against**:
- DDoS attacks (rate limiting)
- Malware (IP reputation)
- Web exploits (SQLi, XSS)
- Bots and scrapers

**Cost**: ~$5/month + per-rule costs

---

## 9. RDS PostgreSQL

**What it is**: AWS-managed PostgreSQL database with pgvector

**SmartFinvo RDS**:
- **Instance**: db.t3.micro (1 vCPU, 1GB RAM) - free tier eligible
- **Storage**: 20GB
- **Backup**: Automatic daily snapshots
- **Location**: Private subnet (not accessible from internet)
- **Port**: 5432 (only from EKS security group)

**What SmartFinvo stores**:
- Users (OAuth identity)
- Expenses (amount, category, date)
- Categories (Groceries, Restaurants, etc.)
- Budget rules
- Expense vectors (pgvector for RAG)

**Cost**: ~$30/month

---

## 10. ElastiCache Redis

**What it is**: AWS-managed in-memory cache

**SmartFinvo Redis**:
- **Instance**: cache.t3.micro
- **Engine**: Redis 7
- **Location**: Private subnet
- **Port**: 6379 (only from EKS security group)

**What SmartFinvo stores in Redis**:
- JWT refresh token hashes (session management)
- Chat conversation memory (AI features)
- Temporary request data

**Cost**: ~$15/month

---

## 11. IAM Roles & Policies

**What they are**: Identity and Access Management - who can do what

**SmartFinvo Roles**:

### EKS Cluster Role
- **Purpose**: Allows EKS control plane to manage AWS resources
- **Permissions**: Describe EC2, create ENIs, manage load balancers
- **Assumed by**: EKS service

### EKS Node Role
- **Purpose**: Allows EC2 worker nodes to:
  - Pull images from ECR
  - Attach EBS volumes
  - Write logs to CloudWatch
  - Call AWS APIs from pod
- **Assumed by**: EC2 worker nodes

### Budgets Action Role
- **Purpose**: Allows AWS Budgets service to stop EC2 instances when spending exceeds 120%
- **Permissions**: ec2:StopInstances

---

## 12. Cost Protection Mechanisms

### Budget Alerts (3 tiers)
- **80%** ($80): Warning email
- **100%** ($100): Alert email
- **120%** ($120): Auto-attach IAM deny policy

### IAM Deny Policy
- **Blocks**: Large EC2 types (only t2.*/t3.* allowed)
- **Blocks**: Resources outside us-east-2
- **Blocks**: RDS, Redshift, SageMaker, Bedrock
- **Blocks**: New IAM users/roles (privilege escalation prevention)

### EventBridge Notifications
- **Triggers on**: EKS cluster create/delete
- **Triggers on**: Node group create/delete
- **Triggers on**: EC2 instance launch/stop/terminate
- **Triggers on**: RDS/Redshift/SageMaker creation
- **Sends**: Email notification within seconds

---

# TERRAFORM FILES EXPLAINED

## File 1: main.tf

**Purpose**: Provider configuration and version requirements

**What it does**:
- Declares required Terraform version (>= 1.5.0)
- Declares AWS provider version (~> 5.0)
- Configures AWS region (us-east-2)
- Reads AWS credentials from ~/.aws/credentials

**Key concept**: No hardcoded credentials - uses AWS CLI configuration

**Analogy**: Like the import statements in Java - tells Terraform which libraries to use

---

## File 2: variables.tf

**Purpose**: All configurable values in one place

**Key variables**:

| Variable | Type | Default | Purpose |
|----------|------|---------|---------|
| `aws_region` | string | us-east-2 | AWS region |
| `cluster_name` | string | smartfinvo | EKS cluster name |
| `kubernetes_version` | string | 1.35 | K8s version |
| `node_instance_type` | string | t3.small | EC2 instance type |
| `node_desired_count` | number | 1 | Desired nodes |
| `node_min_count` | number | 1 | Min nodes for autoscale |
| `node_max_count` | number | 2 | Max nodes for autoscale |
| `vpc_name` | string | smartfinvo-vpc | Existing VPC name |
| `monthly_budget_limit` | number | 100 | Monthly spend ceiling ($) |

**How it works**: Change one variable → all references update automatically

**Analogy**: Like application.properties in Spring Boot - centralized configuration

---

## File 3: data.tf

**Purpose**: Reference existing AWS resources (don't recreate them)

**Data sources created**:
```
data "aws_vpc" "smartfinvo"
  ↓ Finds VPC by tag Name="smartfinvo-vpc"
  ↓ Returns vpc_id = "vpc-0b262b84f18196ea0"

data "aws_subnets" "private"
  ↓ Finds subnets in that VPC with "private" in name
  ↓ Returns [subnet-00c3218b49f93823e, subnet-05f1432f2b065da2a]

data "aws_iam_role" "cluster"
  ↓ Finds IAM role "smartfinvo-eks-cluster-role"
  ↓ Returns ARN for use in cluster resource

data "aws_iam_role" "node"
  ↓ Finds IAM role "smartfinvo-eks-node-role"
  ↓ Returns ARN for use in node group resource
```

**Key concept**: Data sources are READ-ONLY - Terraform won't delete them when you destroy

**Analogy**: Like @Autowired in Spring - injecting existing beans, not creating new ones

---

## File 4: eks.tf

**Purpose**: Create and manage EKS cluster and worker nodes

**Resources created**:

### 1. aws_eks_cluster "smartfinvo"
- Creates the Kubernetes control plane
- Placed in public subnets (for kubectl access)
- Uses cluster IAM role from data.tf
- Kubernetes version from variables.tf

### 2. aws_eks_addon (6 addons)
- **coredns**: DNS service for Kubernetes
- **kube-proxy**: Pod-to-pod networking
- **vpc-cni**: AWS VPC networking for pods
- **eks-pod-identity-agent**: Pod IAM authentication
- **metrics-server**: CPU/memory metrics for autoscaling
- **aws-ebs-csi-driver**: EBS volume mounting

### 3. aws_eks_node_group "smartfinvo"
- Creates EC2 instances (t3.medium)
- Places them in private subnets
- Configures autoscaling (min=1, desired=2, max=5)
- Rolling updates (max 1 unavailable at a time)

**Dependency flow**:
```
aws_eks_cluster (must exist first)
  ↓
aws_eks_addon (depends on cluster)
  ↓
aws_eks_node_group (depends on cluster + addon)
```

**Analogy**: Like creating a Kubernetes cluster via `eksctl` but as code

---

## File 5: security.tf

**Purpose**: Protect against compromised credentials and DDoS attacks

**What it creates**:

### IAM Deny Policy
```
Blocks: EC2 types > t3.medium
Blocks: Resources outside us-east-2
Blocks: RDS, Redshift, SageMaker, Bedrock
Blocks: New IAM users/roles
Applied automatically at 100% budget spend
```

### WAF Web ACL (3 rules)
```
Rule 1: AWS IP Reputation List (blocks malicious IPs)
Rule 2: Rate Limiting (blocks IPs > 1000 req/5min)
Rule 3: Common Exploits (blocks SQLi, XSS, etc.)
```

**Threat model**:
- **Threat 1**: Hacker gets your AWS keys → spins up expensive resources
  - **Defense**: IAM deny policy limits what they can do

- **Threat 2**: Bot attack floods your app → triggers autoscale → high bill
  - **Defense**: WAF rate limit stops the flood at the load balancer

**Analogy**: Like having a security guard (WAF) at the front door checking IDs, plus insurance (IAM policy) that covers you if bad actors get inside

---

## File 6: budget.tf

**Purpose**: Prevent AWS bill surprises

**What it creates**:

### SNS Topic
- Email notifications to coderkali@gmail.com

### Budget with 3 alert levels
- **80%** ($80): Warning email
- **100%** ($100): Alert email + IAM deny policy attached
- **120%** ($120): Email + auto-stop EC2 (if configured)

### IAM Role for Budget Actions
- Permission to stop EC2 instances if budget exceeded

**How it works**:
```
You set: monthly_budget_limit = $100

If spending reaches:
  $80   → "Warning: you've spent $80/$100" (email)
  $100  → "Budget reached!" (email) + IAM deny policy attached
  $120  → "Budget exceeded!" (email) + EC2 nodes stopped (if configured)
```

**Analogy**: Like setting a monthly credit card limit and getting alerts as you approach it

---

## File 7: notifications.tf

**Purpose**: Alert you of any infrastructure changes (security monitoring)

**What it creates**:

### 4 EventBridge Rules (CloudTrail-triggered)

1. **EKS Cluster Changes**
   - Triggers: CreateCluster, DeleteCluster
   - Sends: Email with who, when, what

2. **EKS Node Group Changes**
   - Triggers: CreateNodegroup, DeleteNodegroup
   - Sends: Email with details

3. **EC2 Instance Changes**
   - Triggers: RunInstances, StopInstances, TerminateInstances
   - Sends: Email immediately

4. **Expensive Service Created**
   - Triggers: CreateDBInstance, CreateSageMaker, etc.
   - Sends: ⚠️ SECURITY ALERT email

**Why needed**: Catch unauthorized resource creation (e.g., hacker using your credentials)

**Analogy**: Like motion sensors around your house - instantly alerting you if someone enters

---

## File 8: output.tf

**Purpose**: Display important values after terraform apply

**Outputs**:
- `cluster_name`: smartfinvo
- `cluster_endpoint`: Kubernetes API URL
- `kubectl_connect_command`: Command to run on your Mac
- `deploy_command`: Command to deploy k8s manifests

**Analogy**: Like a summary email after a deployment - gives you the important URLs and commands

---

# KUBERNETES FILES EXPLAINED

## File 1: namespace.yaml

**Purpose**: Logical namespace to isolate resources

**Content**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: smartfinvo
  labels:
    name: smartfinvo
```

**Why needed**: Organize resources, enable multi-tenancy

**Real-world analogy**: Like different departments in a building - each has its own space

---

## File 2: configmap.yaml

**Purpose**: Store non-sensitive configuration data

**What it stores**: Database host, Redis host, API timeouts, feature flags

**Why not in code**: Can change config without rebuilding docker image

**Accessed by**: Spring Boot reads via @ConfigurationProperties

---

## File 3: secret.yaml

**Purpose**: Store sensitive data (passwords, API keys)

**What it stores**: Database password, Redis password, OAuth client secret, OpenAI API key

**Security**: Base64 encoded at rest (not encrypted - use external secrets in production)

**Accessed by**: Spring Boot reads via @Value or environment variables

---

## File 4: postgres/deployment.yaml

**Purpose**: Run PostgreSQL database in Kubernetes

**Key configurations**:
- **Image**: pgvector/pgvector:pg15
- **Storage**: 10GB persistent volume
- **Port**: 5432
- **Resource limits**: 256MB RAM max
- **Health check**: Every 10 seconds

**Why run in K8s**: Simpler setup for dev/test, not recommended for prod (use RDS instead)

---

## File 5: postgres/service.yaml

**Purpose**: Create DNS name for PostgreSQL pod

**Service type**: ClusterIP (internal only, not exposed to internet)

**DNS name**: `postgres.smartfinvo.svc.cluster.local` (accessible from all pods)

**Port mapping**: Port 5432 inside and outside

---

## File 6: redis/deployment.yaml

**Purpose**: Run Redis cache in Kubernetes

**Configuration**:
- **Image**: redis:7-alpine (lightweight)
- **Port**: 6379
- **Memory limit**: 256MB
- **Password**: From secret.yaml

---

## File 7: redis/service.yaml

**Purpose**: Create DNS name for Redis

**Service type**: ClusterIP (internal only)

**DNS name**: `redis.smartfinvo.svc.cluster.local`

---

## File 8: app/deployment.yaml

**Purpose**: Run Spring Boot application

**Key configurations**:
- **Image**: ECR image (smartfinvo:latest)
- **Replicas**: 2 (for high availability)
- **Resource limits**: 768MB RAM, 500m CPU
- **Liveness probe**: Health check every 30 seconds
- **Readiness probe**: Wait 60 seconds before receiving traffic
- **Environment variables**: Database host, Redis host, API keys

**Why 2 replicas**: If one pod crashes, the other keeps serving requests

---

## File 9: app/service.yaml

**Purpose**: Expose Spring Boot app to load balancer

**Service type**: LoadBalancer (creates AWS ALB)

**Port mapping**: Port 8080 inside pod, port 80 on load balancer

**Selector**: Routes traffic only to `app: smartfinvo` pods

---

## File 10: app/hpa.yaml

**Purpose**: Auto-scale pods based on CPU usage

**Configuration**:
- **Min pods**: 2 (always run 2)
- **Max pods**: 5 (never scale beyond 5)
- **CPU threshold**: 70% (scale up if usage > 70%)

**How it works**:
```
Pod CPU usage = 50% → Do nothing (have 2 pods)
Pod CPU usage = 75% → Scale up to 3 pods
Pod CPU usage = 40% → Scale down to 2 pods
```

---

# REQUEST FLOW: USER TO APPLICATION

## Step-by-step journey of an API request

### Step 1: User makes a request
```
User's Mac:
curl http://smartfinvo-alb-123456.us-east-2.elb.amazonaws.com/api/v1/expenses
```

### Step 2: Request enters AWS load balancer
```
ALB (public subnet, us-east-2a)
- Receives request on port 80 (HTTP)
- Route table says: "Send to EKS node group"
```

### Step 3: WAF inspects the request
```
WAF Rules:
1. Check IP reputation → Passed ✓
2. Check rate limit → <1000 req/5min ✓
3. Check for exploits → No SQLi/XSS ✓
Response: ALLOW
```

### Step 4: Request routes to worker node
```
Worker Node 1 (private subnet 10.0.128.0/20)
- Running on EC2 instance (t3.medium)
- Has 2-4 pods of smartfinvo app
- Has system pods (coredns, kube-proxy, vpc-cni)
```

### Step 5: Kubernetes routes to Spring Boot pod
```
kube-proxy (networking layer):
1. Reads service smartfinvo-service
2. Sees 2 pod replicas
3. Does round-robin load balancing
4. Routes to Pod 1 (port 8080)
```

### Step 6: Spring Boot processes request
```
POST /api/v1/expenses

Spring Boot Authentication Filter:
- Reads JWT from Authorization header
- Validates signature and expiry
- Extracts user_id from JWT
- Sets SecurityContext

Spring Boot Controller:
- ExpenseController.createExpense()
- Validates request body (amount > 0, etc.)

Spring Boot Service:
- ExpenseService.createExpense()
- Saves to database

Spring Boot Repository:
- R2DBC sends SQL to PostgreSQL
```

### Step 7: Query PostgreSQL
```
PostgreSQL (private subnet 10.0.128.0/20)
- Running on same EKS cluster OR RDS
- INSERT INTO expense(...)
- Returns new expense row
- Saves pgvector embedding for RAG
```

### Step 8: Check Redis cache
```
Redis (private subnet)
- Stores: JWT refresh token hash
- Stores: Chat conversation memory
- Stores: Session data
- Checked before hitting PostgreSQL
```

### Step 9: Return response to user
```
Spring Boot Controller returns:
{
  "id": "uuid-123",
  "amount": 45.99,
  "category": "groceries",
  "created_at": "2026-03-29T12:00:00Z"
}

HTTP Response:
201 Created
Content-Type: application/json
Body: {...above JSON...}

Load Balancer:
- Sends response back to user (port 80)

User's Mac:
- Receives response
- Displays success
```

### Latency breakdown (typical)
```
WAF inspection:           5ms
LB routing:              2ms
Pod selection:           1ms
Network latency:         5ms
Spring Boot processing: 20ms
PostgreSQL query:       30ms
Response travel:        5ms
────────────────────────────
Total:                ~68ms
```

---

# ARCHITECTURE DIAGRAMS

## Diagram 1: High-Level AWS Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                    AWS Account (274214919013)               │
│                        us-east-2 Region                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────── VPC 10.0.0.0/16 ──────────────┐ │
│  │                                                        │  │
│  │  ┌───────── PUBLIC ─────────┐  ┌────── PUBLIC ──────┐ │  │
│  │  │ Subnet 10.0.0.0/20       │  │ Subnet 10.0.16/20  │ │  │
│  │  │ us-east-2a               │  │ us-east-2b         │ │  │
│  │  │ ┌─────────────────────┐  │  │                    │ │  │
│  │  │ │  ALB                │  │  │ NAT Gateway        │ │  │
│  │  │ │  Load Balancer      │  │  │ (for outbound)     │ │  │
│  │  │ │  Port 80/443        │  │  │                    │ │  │
│  │  │ └────────┬────────────┘  │  │                    │ │  │
│  │  │          │               │  │                    │ │  │
│  │  └──────────┼───────────────┘  └────────────────────┘ │  │
│  │             │                                         │  │
│  │  ┌──────────▼─────────────────────────────────────┐  │  │
│  │  │  ┌──────── PRIVATE ──────────┐  ┌─ PRIVATE ──┐│  │  │
│  │  │  │ Subnet 10.0.128.0/20      │  │ 10.0.144/20││  │  │
│  │  │  │ us-east-2a                │  │ us-east-2b ││  │  │
│  │  │  │ ┌────────────────┐        │  │            ││  │  │
│  │  │  │ │ EKS Node 1     │        │  │ EKS Node 2 ││  │  │
│  │  │  │ │ t3.medium      │        │  │ t3.medium  ││  │  │
│  │  │  │ │                │        │  │            ││  │  │
│  │  │  │ │ ┌─────────────┐│        │  │ ┌────────┐ ││  │  │
│  │  │  │ │ │ Spring Boot ││        │  │ │Spring  │ ││  │  │
│  │  │  │ │ │ Pod 1       ││        │  │ │Boot    │ ││  │  │
│  │  │  │ │ │ Port 8080   ││        │  │ │Pod 2   │ ││  │  │
│  │  │  │ │ └─────────────┘│        │  │ │8080    │ ││  │  │
│  │  │  │ │                │        │  │ └────────┘ ││  │  │
│  │  │  │ │ ┌─────────────┐│        │  │            ││  │  │
│  │  │  │ │ │ PostgreSQL  ││        │  │            ││  │  │
│  │  │  │ │ │ or RDS      ││        │  │            ││  │  │
│  │  │  │ │ │ Port 5432   ││        │  │            ││  │  │
│  │  │  │ │ └─────────────┘│        │  │            ││  │  │
│  │  │  │ │                │        │  │            ││  │  │
│  │  │  │ │ ┌─────────────┐│        │  │            ││  │  │
│  │  │  │ │ │ Redis Cache ││        │  │            ││  │  │
│  │  │  │ │ │ Port 6379   ││        │  │            ││  │  │
│  │  │  │ │ └─────────────┘│        │  │            ││  │  │
│  │  │  │ │                │        │  │            ││  │  │
│  │  │  │ └────────────────┘        │  │            ││  │  │
│  │  │  │                           │  │            ││  │  │
│  │  │  │ ┌─ KUBERNETES ───────────┘  └────────────┘│  │  │
│  │  │  │ │ CONTROL PLANE                          │  │  │
│  │  │  │ │ (Managed by AWS)                       │  │  │
│  │  │  │ └────────────────────────────────────────┘  │  │
│  │  │  └────────────────────────────────────────────┘  │  │
│  │  │                                                   │  │
│  └──┴───────────────────────────────────────────────────┘ │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  IAM Roles:                                         │  │
│  │  - smartfinvo-eks-cluster-role                      │  │
│  │  - smartfinvo-eks-node-role                         │  │
│  │  - smartfinvo-budgets-action-role                   │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  Cost Protection:                                   │  │
│  │  - Monthly Budget: $100 (with 80/100/120 alerts)   │  │
│  │  - IAM Deny Policy (blocks expensive services)     │  │
│  │  - WAF Rate Limiting (1000 req/5min per IP)        │  │
│  │  - EventBridge Notifications                       │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## Diagram 2: Request Flow through AWS
```
USER (Mac Browser)
        │
        │ curl /api/v1/expenses
        │
        ▼
┌─────────────────────────────────────────────┐
│  INTERNET                                   │
│  0.0.0.0/0                                  │
└─────────────────────────────────────────────┘
        │
        │ Port 80/443
        │
        ▼
┌─────────────────────────────────────────────┐
│  INTERNET GATEWAY                           │
│  Attaches VPC to internet                   │
└─────────────────────────────────────────────┘
        │
        │ Route 0.0.0.0/0 → IGW
        │
        ▼
┌─────────────────────────────────────────────┐
│  PUBLIC SUBNETS                             │
│  10.0.0.0/20 (us-east-2a)                   │
│  10.0.16.0/20 (us-east-2b)                  │
└─────────────────────────────────────────────┘
        │
        │ Route to ALB
        │
        ▼
┌─────────────────────────────────────────────┐
│  APPLICATION LOAD BALANCER (ALB)            │
│  smartfinvo-alb-123456.elb.amazonaws.com    │
│  Port 80 → Port 8080 on pods                │
└─────────────────────────────────────────────┘
        │
        │ Check WAF rules (3 layers)
        │ 1. IP Reputation
        │ 2. Rate Limit (1000 req/5min)
        │ 3. Common Exploits (SQLi, XSS)
        │
        ▼ ALLOWED
┌─────────────────────────────────────────────┐
│  ROUTE TABLE                                │
│  Routes: 10.0.0.0/16 → Local                │
│          0.0.0.0/0 → IGW                    │
│  Sends traffic to private subnets           │
└─────────────────────────────────────────────┘
        │
        │ Route to target group (EKS nodes)
        │
        ▼
┌─────────────────────────────────────────────┐
│  PRIVATE SUBNETS (EKS NODES)                │
│  10.0.128.0/20 (us-east-2a) - Node 1        │
│  10.0.144.0/20 (us-east-2b) - Node 2        │
└─────────────────────────────────────────────┘
        │
        │ kube-proxy load balances to pods
        │ Round-robin across 2 Spring Boot pods
        │
        ├───────────────────┬──────────────────┐
        │                   │                  │
        ▼                   ▼                  ▼
    Pod 1 (Node 1)    Pod 2 (Node 2)    [Backup Pod]
    Spring Boot       Spring Boot
    Port 8080         Port 8080
        │                   │
        │                   │
        └───────────┬───────┘
                    │
                    │ SQL Queries
                    │ GET cache from Redis
                    │ AI embeddings from pgvector
                    │
        ┌───────────┼────────────┬──────────────┐
        │           │            │              │
        ▼           ▼            ▼              ▼
    PostgreSQL   Redis       ECR             OpenAI
    (RDS)        (Cache)     (Images)        (API)
    Port 5432    Port 6379
        │           │            │              │
        └───────────┼────────────┴──────────────┘
                    │
                    │ Response JSON
                    │
        ▼
    Spring Boot Pod (returns 201 Created)
        │
        │ Response
        │
        ▼
    ALB (port 80)
        │
        │ Response over HTTP
        │
        ▼
    USER'S BROWSER
        │
        │ Display results
        │
        ▼ SUCCESS
```

## Diagram 3: Kubernetes Architecture (Inside EKS Cluster)
```
┌──────────────────────────────────────────────────────────────┐
│                    EKS CLUSTER (K8s)                         │
│                  Control Plane (AWS-managed)                 │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ API Server      │  │ Scheduler    │  │ Controllers   │  │
│  │ (kubectl talks  │  │ (places pods │  │ (health      │  │
│  │  to this)       │  │  on nodes)   │  │  checks)     │  │
│  └────────┬────────┘  └──────────────┘  └───────────────┘  │
│           │                                                  │
│           │ etcd (stores cluster state)                     │
│           │                                                  │
│  ┌────────▼──────────────────────────────────────────────┐  │
│  │            NAMESPACE: smartfinvo                      │  │
│  │                                                       │  │
│  │  ┌──────────────────────────────────────────────┐   │  │
│  │  │ DEPLOYMENTS (manage pods)                    │   │  │
│  │  │                                              │   │  │
│  │  │ ┌──────────────────────────────────────┐    │   │  │
│  │  │ │ smartfinvo (Spring Boot) - 2 replicas│    │   │  │
│  │  │ └────────────────┬─────────────────────┘    │   │  │
│  │  │                  │ ┌──────┐  ┌──────┐       │   │  │
│  │  │                  ├─┤ Pod1 │  │ Pod2 │       │   │  │
│  │  │                  │ └──────┘  └──────┘       │   │  │
│  │  │ ┌──────────────────────────────────────┐    │   │  │
│  │  │ │ PostgreSQL (statefulset)             │    │   │  │
│  │  │ └─────┬────────────────────────────────┘    │   │  │
│  │  │       │ ┌─ Pod: postgres-0 (5432)           │   │  │
│  │  │ ┌──────────────────────────────────────┐    │   │  │
│  │  │ │ Redis (statefulset)                  │    │   │  │
│  │  │ └─────┬──────────────────────────────┘     │   │  │
│  │  │       │ ┌─ Pod: redis-0 (6379)            │   │  │
│  │  │                                            │   │  │
│  │  └──────────────────────────────────────────┘   │   │  │
│  │                                                  │   │  │
│  │  ┌──────────────────────────────────────────┐   │   │  │
│  │  │ SERVICES (expose pods)                   │   │   │  │
│  │  │                                          │   │   │  │
│  │  │ smartfinvo-service (LoadBalancer)        │   │   │  │
│  │  │   ├─ Type: LoadBalancer (creates ALB)   │   │   │  │
│  │  │   ├─ Port 80 → Pod Port 8080            │   │   │  │
│  │  │   └─ Selector: app: smartfinvo          │   │   │  │
│  │  │                                          │   │   │  │
│  │  │ postgres-service (ClusterIP)             │   │   │  │
│  │  │   ├─ Type: ClusterIP (internal DNS)     │   │   │  │
│  │  │   ├─ Port 5432                          │   │   │  │
│  │  │   └─ DNS: postgres.smartfinvo...        │   │   │  │
│  │  │                                          │   │   │  │
│  │  │ redis-service (ClusterIP)                │   │   │  │
│  │  │   ├─ Type: ClusterIP (internal DNS)     │   │   │  │
│  │  │   ├─ Port 6379                          │   │   │  │
│  │  │   └─ DNS: redis.smartfinvo...           │   │   │  │
│  │  └──────────────────────────────────────────┘   │   │  │
│  │                                                  │   │  │
│  │  ┌──────────────────────────────────────────┐   │   │  │
│  │  │ HPA (Horizontal Pod Autoscaler)          │   │   │  │
│  │  │ - Min: 2 pods                            │   │   │  │
│  │  │ - Max: 5 pods                            │   │   │  │
│  │  │ - Scale at: 70% CPU usage                │   │   │  │
│  │  └──────────────────────────────────────────┘   │   │  │
│  │                                                  │   │  │
│  │  ┌──────────────────────────────────────────┐   │   │  │
│  │  │ CONFIGMAP (non-sensitive config)         │   │   │  │
│  │  │ - database.host = postgres.smartfinvo... │   │   │  │
│  │  │ - redis.host = redis.smartfinvo...       │   │   │  │
│  │  │ - app.jwt.expiry = 900000                │   │   │  │
│  │  └──────────────────────────────────────────┘   │   │  │
│  │                                                  │   │  │
│  │  ┌──────────────────────────────────────────┐   │   │  │
│  │  │ SECRET (sensitive data)                  │   │   │  │
│  │  │ - database.password = (base64)           │   │   │  │
│  │  │ - redis.password = (base64)              │   │   │  │
│  │  │ - openai.api-key = (base64)              │   │   │  │
│  │  └──────────────────────────────────────────┘   │   │  │
│  │                                                  │   │  │
│  └──────────────────────────────────────────────────┘   │  │
│                                                         │  │
│  ┌─────────────────────────────────────────────────┐  │  │
│  │ ADD-ONS (system pods)                           │  │  │
│  │ ┌───────────┬───────────┬──────────┬─────────┐ │  │  │
│  │ │ CoreDNS   │ kube-     │ vpc-cni  │ metrics │ │  │  │
│  │ │ (DNS)     │ proxy     │ (network)│ server  │ │  │  │
│  │ └───────────┴───────────┴──────────┴─────────┘ │  │  │
│  └─────────────────────────────────────────────────┘  │  │
│                                                       │  │
└──────────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ WORKER NODES (EC2 instances)                     │  │
│  │ ┌──────────────────┐  ┌──────────────────┐       │  │
│  │ │ Node 1           │  │ Node 2           │       │  │
│  │ │ EC2: t3.medium   │  │ EC2: t3.medium   │       │  │
│  │ │ 2 vCPU, 4GB RAM  │  │ 2 vCPU, 4GB RAM  │       │  │
│  │ │ Subnet: 10.0.128 │  │ Subnet: 10.0.144 │       │  │
│  │ │ AZ: us-east-2a   │  │ AZ: us-east-2b   │       │  │
│  │ └──────────────────┘  └──────────────────┘       │  │
│  │                                                   │  │
│  │ Each node runs:                                  │  │
│  │ - kubelet (talks to API server)                 │  │
│  │ - kube-proxy (networking)                       │  │
│  │ - container runtime (Docker)                    │  │
│  │ - CNI plugin (AWS VPC CNI)                      │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

# TERRAFORM → KUBERNETES → AWS EXECUTION FLOW
```
YOU RUN: terraform apply

    ↓

[1. main.tf]
    │ Declares AWS provider
    │ Authenticates using ~/.aws/credentials
    ├─→ Terraform can now talk to AWS API
    
    ↓

[2. variables.tf]
    │ Loads all variables (cluster_name=smartfinvo, etc.)
    │ Provides defaults
    ├─→ Terraform knows what to build

    ↓

[3. data.tf]
    │ Queries AWS for existing resources:
    │   - VPC vpc-0b262b84f18196ea0
    │   - Subnets (private)
    │   - IAM roles
    │
    ├─→ terraform plan shows "Read: VPC..." "Read: Subnets..." etc
    ├─→ If not found, plan FAILS

    ↓

[4. eks.tf]
    │ Creates:
    │   - aws_eks_cluster "smartfinvo"
    │   - aws_eks_addon (6 of them)
    │   - aws_eks_node_group "smartfinvo"
    │
    ├─→ AWS spins up Kubernetes control plane (5 mins)
    ├─→ AWS spins up t3.medium EC2 instances (8 mins)
    ├─→ Nodes join cluster and become Ready

    ↓

[5. security.tf]
    │ Creates:
    │   - aws_iam_policy (cost guardrails)
    │   - aws_wafv2_web_acl (3 rules)
    │   - aws_budgets_budget_action (attach policy at 100%)
    │
    ├─→ WAF is attached to ALB (via k8s service)
    ├─→ IAM policy ready (triggered by budget)

    ↓

[6. budget.tf]
    │ Creates:
    │   - aws_sns_topic (billing alerts)
    │   - aws_budgets_budget (3 alert levels)
    │   - aws_iam_role (for budget actions)
    │
    ├─→ Email sent to you: "Confirm SNS subscription"
    ├─→ Budget monitoring active

    ↓

[7. notifications.tf]
    │ Creates:
    │   - aws_cloudwatch_event_rule (4 rules)
    │   - aws_cloudwatch_event_target (to SNS)
    │
    ├─→ EventBridge watches CloudTrail for changes
    ├─→ Any EKS/EC2 change → email alert

    ↓

[8. output.tf]
    │ Displays important values to you:
    │   - Cluster name: smartfinvo
    │   - Cluster endpoint: https://xxx.eks.amazonaws.com
    │   - kubectl command: aws eks update-kubeconfig...
    │
    ├─→ Copy these and run them on your Mac

    ↓

NOW YOU RUN: aws eks update-kubeconfig --region us-east-2 --name smartfinvo
    └─→ Updates ~/.kube/config
    └─→ kubectl can now talk to your cluster

    ↓

NOW YOU RUN: kubectl apply -R -f k8s/
    │
    ├─→ [namespace.yaml] Creates namespace "smartfinvo"
    ├─→ [configmap.yaml] Creates ConfigMap (config data)
    ├─→ [secret.yaml] Creates Secret (passwords, API keys)
    ├─→ [postgres/deployment.yaml] Creates PostgreSQL pod
    ├─→ [postgres/service.yaml] Exposes PostgreSQL via DNS
    ├─→ [redis/deployment.yaml] Creates Redis pod
    ├─→ [redis/service.yaml] Exposes Redis via DNS
    ├─→ [app/deployment.yaml] Creates 2 Spring Boot pods
    ├─→ [app/service.yaml] Creates LoadBalancer (ALB)
    ├─→ [app/hpa.yaml] Creates autoscaler (min 2, max 5 pods)
    │
    ├─→ All pods start communicating via internal DNS

    ↓

RESULT:
- Terraform created AWS infrastructure
- Kubernetes is running on top of AWS
- Your application is live at ALB URL
```

---

# SUMMARY TABLE: Files, Components, and Their Purposes

| Component | File | Category | Purpose | Where Used | Dependency | Key Points |
|-----------|------|----------|---------|-----------|-----------|-----------|
| **AWS Provider** | main.tf | Terraform | Configure AWS connection | Global | None | Authenticates via ~/.aws/credentials |
| **Variables** | variables.tf | Terraform | Centralized configuration | All Terraform files | None | Change here → everything updates |
| **Data Sources** | data.tf | Terraform | Reference existing AWS resources | eks.tf, security.tf | Pre-existing AWS resources | Read-only, won't be deleted |
| **EKS Cluster** | eks.tf | Terraform | Create K8s control plane & nodes | Global | data.tf | Takes 10-15 mins to create |
| **Security** | security.tf | Terraform | IAM policies, WAF rules | Global | data.tf, budget.tf | Protects against exploits & DDoS |
| **Budget** | budget.tf | Terraform | Cost protection & alerts | Global | None | Sends email alerts, auto-attaches IAM deny |
| **Notifications** | notifications.tf | Terraform | EventBridge rules for alerts | Global | budget.tf (SNS topic) | Watches CloudTrail for changes |
| **Outputs** | output.tf | Terraform | Display important values | Terminal | eks.tf | Shows kubectl command |
| **Namespace** | namespace.yaml | Kubernetes | Logical grouping of resources | K8s cluster | None | Isolates smartfinvo resources |
| **ConfigMap** | configmap.yaml | Kubernetes | Non-sensitive configuration | App deployment | Namespace | Mounted as env vars or files |
| **Secret** | secret.yaml | Kubernetes | Sensitive data (passwords, keys) | App deployment | Namespace | Base64 encoded (not encrypted) |
| **PostgreSQL** | postgres/deployment.yaml | Kubernetes | Run database pod | K8s cluster | Namespace, Persistent Volume | Stores all app data |
| **Postgres Service** | postgres/service.yaml | Kubernetes | Expose database via DNS | Spring Boot pods | PostgreSQL pod | Internal DNS: postgres.smartfinvo... |
| **Redis** | redis/deployment.yaml | Kubernetes | Run cache pod | K8s cluster | Namespace | Stores JWT tokens, chat memory |
| **Redis Service** | redis/service.yaml | Kubernetes | Expose cache via DNS | Spring Boot pods | Redis pod | Internal DNS: redis.smartfinvo... |
| **Spring Boot** | app/deployment.yaml | Kubernetes | Run application | K8s cluster | Namespace, ConfigMap, Secret | 2 replicas for HA |
| **ALB Service** | app/service.yaml | Kubernetes | Create load balancer | ALB on AWS | App deployment | Type: LoadBalancer → creates ALB |
| **HPA** | app/hpa.yaml | Kubernetes | Auto-scale pods | Scheduler | App deployment | Min 2, max 5 pods, 70% CPU threshold |
| **VPC** | data.tf (read) | AWS | Network isolation | All resources | Manual creation | 10.0.0.0/16 |
| **Subnets** | data.tf (read) | AWS | Network segments | EKS, RDS, Redis | VPC | 2 public, 2 private |
| **IAM Roles** | data.tf (read) | AWS | Permission boundaries | EKS, nodes, budget | Manual creation | Cluster role, node role |
| **ALB** | app/service.yaml (created) | AWS | Load balancing | Public internet | Service | Port 80 → pod 8080 |
| **WAF** | security.tf | AWS | Attack protection | ALB | None | 3 rules: IP rep, rate limit, exploits |
| **RDS PostgreSQL** | Manual or RDS | AWS | Managed database | Spring Boot | VPC, subnets | Alternative to K8s pod |
| **ElastiCache Redis** | Manual or ElastiCache | AWS | Managed cache | Spring Boot | VPC, subnets | Alternative to K8s pod |

---

## KEY INSIGHTS FOR INTERVIEWS

### 1. **Why Kubernetes on AWS?**
- Portable: Code runs same way on any cloud
- Scalable: HPA auto-adds pods during traffic spikes
- Resilient: If one node fails, pods move to another node
- Declarative: You describe what you want (yaml), K8s makes it happen

### 2. **Why multiple components?**
```
ALB      → Distributes traffic across nodes (AWS layer)
K8s      → Distributes pods across nodes (K8s layer)
HPA      → Scales pods based on CPU (K8s layer)
Budget   → Limits spending (AWS layer)
WAF      → Blocks attacks (AWS layer)
```

All layers work together for a resilient, scalable, cost-controlled system.

### 3. **Latency breakdown**
```
Network:          ~10ms (from user to ALB)
WAF inspection:   ~5ms (3 rules checked)
LB routing:       ~2ms (ALB decides target)
Pod scheduling:   ~1ms (kube-proxy routes)
App processing:   ~20ms (Spring Boot logic)
Database query:   ~30ms (PostgreSQL execution)
────────────────────────────
Total:           ~68ms for a typical request
```

### 4. **Cost breakdown**
```
EKS control plane: FREE (managed by AWS)
EC2 nodes:        ~$34/month (t3.medium × 2)
RDS PostgreSQL:   ~$30/month (db.t3.micro)
ElastiCache Redis: ~$15/month (cache.t3.micro)
ALB:              ~$16/month + data transfer
Bandwidth/misc:   ~$5/month
────────────────────────────
Total:           ~$100/month
```

### 5. **Security layers**
```
Layer 1: IAM (who can do what)
Layer 2: Security groups (firewall)
Layer 3: WAF (HTTP attack protection)
Layer 4: K8s network policies (pod-to-pod)
Layer 5: Application-level auth (JWT)
Layer 6: Encryption (HTTPS, DB passwords)
```

### 6. **High Availability Design**
```
- 2 nodes across 2 AZs (zone failure tolerance)
- 2 pod replicas (pod failure tolerance)
- ALB health checks every 30s
- Multi-replica pattern for everything stateless
- RDS multi-AZ (automatic failover)
```

---

**END OF GUIDE**

This document provides enterprise-level understanding of SmartFinvo's architecture. Use it as a reference for interviews, system design discussions, or onboarding new engineers.

