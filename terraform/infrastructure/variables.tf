# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure Module Variables
#
# All configurable values for the infrastructure module in one place.
# Change here → everything in this module updates automatically.
#
# This module is SELF-CONTAINED — no references to the cicd/ module.
# Can be deployed independently before or after CI/CD.
# ══════════════════════════════════════════════════════════════════

# ── AWS Configuration ─────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region where all infrastructure resources are deployed"
  type        = string
  default     = "us-east-2"
  # All resources (EKS, VPC, WAF) must be in the same region
}

# ── EKS Cluster Configuration ─────────────────────────────────────

variable "cluster_name" {
  description = "EKS cluster name — used as prefix for all related resource names"
  type        = string
  default     = "smartfinvo"
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.35"
  # Note: AWS auto-updates minor versions. Terraform ignores version drift
  # via lifecycle { ignore_changes = [version] }
}

# ── Node Group Configuration ──────────────────────────────────────

variable "node_instance_type" {
  description = "EC2 instance type for EKS worker nodes"
  type        = string
  default     = "t3.small"
  # t3.small  = 2 vCPU, 2 GB RAM = ~$17/month — dev/test
  # t3.medium = 2 vCPU, 4 GB RAM = ~$34/month — production
  # t3.large  = 2 vCPU, 8 GB RAM = ~$67/month — high memory workloads
}

variable "node_desired_count" {
  description = "Number of worker nodes to run normally"
  type        = number
  default     = 1
  # 1 = cost-optimized for dev/test
  # 2 = recommended for production (high availability across 2 AZs)
}

variable "node_min_count" {
  description = "Minimum worker nodes (cluster autoscaler will not go below this)"
  type        = number
  default     = 1
  # Keep at 1 — cluster must always have at least one node for system pods
}

variable "node_max_count" {
  description = "Maximum worker nodes (cluster autoscaler will not exceed this)"
  type        = number
  default     = 2
  # Cost control ceiling — prevents runaway scaling
}

# ── Existing AWS Resources ────────────────────────────────────────
# These resources were created manually in AWS Console.
# Terraform will REFERENCE them (not create or delete them).

variable "vpc_name" {
  description = "Name tag of the existing VPC where EKS will be deployed"
  type        = string
  default     = "smartfinvo-vpc"
  # Find with: aws ec2 describe-vpcs --filters "Name=tag:Name,Values=smartfinvo-vpc"
}

variable "cluster_role_name" {
  description = "Name of the existing IAM role for the EKS cluster control plane"
  type        = string
  default     = "smartfinvo-eks-cluster-role"
  # This role was created manually. Must have AmazonEKSClusterPolicy attached.
}

variable "node_role_name" {
  description = "Name of the existing IAM role for EKS worker nodes (EC2 instances)"
  type        = string
  default     = "smartfinvo-eks-node-role"
  # This role was created manually. Must have:
  #   - AmazonEKSWorkerNodePolicy
  #   - AmazonEKS_CNI_Policy
  #   - AmazonEC2ContainerRegistryReadOnly
}

# ── Cost Protection Configuration ─────────────────────────────────

variable "monthly_budget_limit" {
  description = "Maximum monthly AWS spend in USD before auto-stop and guardrails trigger"
  type        = number
  default     = 100
  # EKS control plane = ~$73/month
  # t3.small × 1 node = ~$17/month
  # Misc (data transfer, logs) = ~$2–5/month
  # Total expected ≈ $92–95/month — $100 gives ~$5–8 buffer
}

variable "alert_email" {
  description = "Email address to receive billing alerts and security notifications"
  type        = string
  default     = "coderkali@gmail.com"
  # You must click the confirmation email AWS sends after first terraform apply
}

variable "iam_user_name" {
  description = "AWS IAM username — the cost guardrails policy is attached to this user at 100% budget"
  type        = string
  default     = "kaliprasad"
  # Find yours with: aws iam get-user --query 'User.UserName' --output text
}

# ── Security Configuration ────────────────────────────────────────

variable "waf_rate_limit" {
  description = "Maximum HTTP requests per 5-minute window per IP before WAF blocks it"
  type        = number
  default     = 1000
  # 1000 req/5min = ~3.3 req/sec — sufficient for real users
  # A DDoS bot typically sends thousands per second
  # Lower to 300–500 for stricter protection (may affect legitimate traffic)
}