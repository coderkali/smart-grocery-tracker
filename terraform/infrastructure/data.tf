# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure Data Sources
#
# Data sources READ existing AWS resources without creating them.
# These resources were created manually in the AWS Console.
#
# How data sources work:
#   data "resource_type" "local_name" { filter }
#     resource_type = what kind of AWS resource to look up
#     local_name    = how we reference it elsewhere (data.TYPE.NAME)
#     filter        = how to identify it in AWS (by tag, name, etc.)
#
# After terraform apply, Terraform knows the IDs of these resources
# and can pass them to other resources like the EKS cluster.
# ══════════════════════════════════════════════════════════════════

# ── Existing VPC ──────────────────────────────────────────────────
# Finds the smartfinvo-vpc by its Name tag.
#
# After this block you can use:
#   data.aws_vpc.smartfinvo.id  →  "vpc-0b262b84f18196ea0"
#   data.aws_vpc.smartfinvo.cidr_block  →  e.g. "10.0.0.0/16"

data "aws_vpc" "smartfinvo" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]   # "smartfinvo-vpc" — set in variables.tf
  }
}

# ── Existing Private Subnets ──────────────────────────────────────
# Finds all private subnets inside the smartfinvo VPC.
# EKS worker nodes and cluster endpoints are placed in private subnets.
#
# After this block you can use:
#   data.aws_subnets.private.ids  →  ["subnet-00c3218b...", "subnet-05f1432f..."]
#
# Filter 1: Only subnets inside our VPC (by vpc-id)
# Filter 2: Only subnets with "private" anywhere in their Name tag

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.smartfinvo.id]
  }
  filter {
    name   = "tag:Name"
    values = ["*private*"]  # matches smartfinvo-subnet-private1 and private2
  }
}

# ── Existing EKS Cluster IAM Role ────────────────────────────────
# Finds the IAM role used by the EKS control plane.
# This role allows AWS to manage the Kubernetes API server on your behalf.
#
# After this block you can use:
#   data.aws_iam_role.cluster.arn  →  full ARN of smartfinvo-eks-cluster-role

data "aws_iam_role" "cluster" {
  name = var.cluster_role_name  # "smartfinvo-eks-cluster-role"
}

# ── Existing EKS Node IAM Role ────────────────────────────────────
# Finds the IAM role used by EC2 worker nodes.
# This role allows worker nodes to call ECR, CloudWatch, and EKS APIs.
#
# After this block you can use:
#   data.aws_iam_role.node.arn  →  full ARN of smartfinvo-eks-node-role

data "aws_iam_role" "node" {
  name = var.node_role_name  # "smartfinvo-eks-node-role"
}