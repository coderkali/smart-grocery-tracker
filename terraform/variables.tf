# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Terraform Variables
#
# All configurable values in one place.
# Change here → everything else updates automatically.
#
# Usage in other files: var.cluster_name, var.aws_region etc.
# ══════════════════════════════════════════════════════════════════

# ── AWS Configuration ─────────────────────────────────────────────
variable "aws_region" {
  description = "AWS region where all resources live"
  type        = string
  default     = "us-east-2"
  # Must match where VPC and ECR already exist
}

# ── Cluster Configuration ─────────────────────────────────────────
variable "cluster_name" {
  description = "EKS cluster name — used to name all related resources"
  type        = string
  default     = "smartfinvo"
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.35"
}

# ── Node Configuration ────────────────────────────────────────────
variable "node_instance_type" {
  description = "EC2 instance type for worker nodes"
  type        = string
  default     = "t3.medium"
  # t3.small = 2 vCPU, 2GB RAM = $0.023/hour
  # t3.medium = 2 vCPU, 4GB RAM = $0.047/hour (if app needs more memory)
}

variable "node_desired_count" {
  description = "Number of nodes to run normally"
  type        = number
  default     = 2
  # 2 nodes = high availability across 2 AZs
}

variable "node_min_count" {
  description = "Minimum nodes (HPA can scale down to this)"
  type        = number
  default     = 1
  # 1 minimum = saves money during quiet periods
}

variable "node_max_count" {
  description = "Maximum nodes (HPA can scale up to this)"
  type        = number
  default     = 3
  # 3 maximum = cost control ceiling
}

# ── Existing Resources ────────────────────────────────────────────
# These already exist — Terraform will reference not recreate them

variable "vpc_name" {
  description = "Name tag of the existing VPC"
  type        = string
  default     = "smartfinvo-vpc"
}

variable "cluster_role_name" {
  description = "Name of existing EKS cluster IAM role"
  type        = string
  default     = "smartfinvo-eks-cluster-role"
}

variable "node_role_name" {
  description = "Name of existing EKS node IAM role"
  type        = string
  default     = "smartfinvo-eks-node-role"
}