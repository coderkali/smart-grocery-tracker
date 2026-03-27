# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Data Sources
#
# These blocks READ existing AWS resources.
# Terraform does NOT create or delete these.
# They were created manually in AWS Console.
#
# data "resource_type" "local_name" { filter }
#   resource_type = what kind of AWS resource
#   local_name    = how we refer to it in other files
#   filter        = how to find it in AWS
# ══════════════════════════════════════════════════════════════════

# ── Existing VPC ──────────────────────────────────────────────────
# Finds our smartfinvo-vpc by its Name tag.
# After this block we can use:
#   data.aws_vpc.smartfinvo.id
#   → returns "vpc-0b262b84f18196ea0"

data "aws_vpc" "smartfinvo" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]   # "smartfinvo-vpc" from variables.tf
  }
}

# ── Existing Private Subnets ──────────────────────────────────────
# Finds all private subnets in our VPC.
# After this block we can use:
#   data.aws_subnets.private.ids
#   → returns ["subnet-00c3218b49f93823e", "subnet-05f1432f2b065da2a"]
#
# Filter 1: Only subnets inside our VPC
# Filter 2: Only subnets with "private" in their Name tag

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.smartfinvo.id]
  }
  filter {
    name   = "tag:Name"
    values = ["*private*"]    # matches smartfinvo-subnet-private1 and private2
  }
}

# ── Existing EKS Cluster IAM Role ────────────────────────────────
# Finds the IAM role we created for the EKS control plane.
# After this block we can use:
#   data.aws_iam_role.cluster.arn
#   → returns full ARN of smartfinvo-eks-cluster-role

data "aws_iam_role" "cluster" {
  name = var.cluster_role_name  # "smartfinvo-eks-cluster-role"
}

# ── Existing EKS Node IAM Role ────────────────────────────────────
# Finds the IAM role we created for EC2 worker nodes.
# After this block we can use:
#   data.aws_iam_role.node.arn
#   → returns full ARN of smartfinvo-eks-node-role

data "aws_iam_role" "node" {
  name = var.node_role_name     # "smartfinvo-eks-node-role"
}