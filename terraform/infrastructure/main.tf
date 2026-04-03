# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure Module
#
# This module manages all core infrastructure:
#   1. EKS Cluster      — Kubernetes control plane
#   2. EKS Add-ons      — CoreDNS, kube-proxy, VPC CNI, metrics-server
#   3. Node Group       — EC2 worker nodes where pods run
#
# Related files in this module:
#   iam.tf          — Budget role, cost guardrails IAM policy
#   budget.tf       — SNS alerts, monthly budget + notifications
#   security.tf     — WAF web ACL, budget action (auto-attach policy)
#   notifications.tf — EventBridge rules for infrastructure changes
#   data.tf         — Reads existing VPC, subnets, IAM roles
#   variables.tf    — All configurable values
#   outputs.tf      — EKS cluster info, kubectl commands
#
# INDEPENDENT MODULE: Can be deployed without the cicd/ module.
#
# DEPLOY:
#   cd terraform/infrastructure/
#   terraform init
#   terraform plan
#   terraform apply
#
# CONNECT kubectl after apply:
#   aws eks update-kubeconfig --region us-east-2 --name smartfinvo
# ══════════════════════════════════════════════════════════════════

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# AWS provider — credentials from ~/.aws/credentials (aws configure)
provider "aws" {
  region = var.aws_region
}

# ══════════════════════════════════════════════════════════════════
# EKS Cluster — Kubernetes Control Plane
#
# The EKS control plane manages the Kubernetes API server, etcd,
# and scheduler. AWS manages this for you — you only manage nodes.
#
# vpc_config:
#   - endpoint_public_access  = true  → kubectl from your Mac works
#   - endpoint_private_access = true  → nodes communicate internally
#   - subnet_ids = private subnets    → control plane in private network
#
# Cost: ~$73/month for the managed control plane (always-on)
# ══════════════════════════════════════════════════════════════════

resource "aws_eks_cluster" "smartfinvo" {
  name     = var.cluster_name        # "smartfinvo"
  version  = var.kubernetes_version  # "1.35"
  role_arn = data.aws_iam_role.cluster.arn

  # VPC and subnet placement for the cluster
  vpc_config {
    subnet_ids = data.aws_subnets.private.ids

    # Public endpoint  = kubectl works from your Mac (external access)
    # Private endpoint = node-to-API-server traffic stays in VPC
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  # Authentication settings
  access_config {
    # API mode = modern EKS access entries (replaces aws-auth ConfigMap)
    authentication_mode = "API"

    # The IAM identity that ran terraform apply gets cluster admin access
    bootstrap_cluster_creator_admin_permissions = true
  }

  # If AWS auto-updates the cluster version, Terraform should not
  # try to roll it back — that would cause downtime
  lifecycle {
    ignore_changes = [version]
  }
}

# ══════════════════════════════════════════════════════════════════
# EKS Add-ons — Core Cluster Components
#
# Add-ons are AWS-managed plugins that run as pods in kube-system.
# AWS keeps these updated and monitors their health automatically.
#
# coredns         — DNS server for service discovery between pods
# kube-proxy      — Network rules for Kubernetes Services (ClusterIP/NodePort)
# vpc-cni         — Assigns VPC IP addresses directly to pods
# pod-identity    — Allows pods to use IAM roles (IRSA replacement)
# metrics-server  — Provides CPU/memory metrics for HPA scaling
# ebs-csi-driver  — Enables PersistentVolumes backed by EBS disks
# ══════════════════════════════════════════════════════════════════

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "coredns"
  depends_on   = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "kube-proxy"
  depends_on   = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "vpc-cni"
  depends_on   = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "pod_identity" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "eks-pod-identity-agent"
  depends_on   = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "metrics_server" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "metrics-server"
  depends_on   = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "ebs_csi_driver" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "aws-ebs-csi-driver"

  # EBS CSI driver needs at least one node to run on
  depends_on = [
    aws_eks_cluster.smartfinvo,
    aws_eks_node_group.smartfinvo
  ]
}

# ══════════════════════════════════════════════════════════════════
# EKS Node Group — EC2 Worker Nodes
#
# These are the EC2 instances where your application pods actually run.
# AWS manages the node lifecycle (launch, join cluster, health checks).
#
# Placement: private subnets — nodes are NOT directly internet-accessible.
#   Traffic from internet → Load Balancer → Pods on private nodes
#
# Scaling:
#   desired = how many nodes run normally
#   min     = cluster autoscaler won't go below this
#   max     = cost ceiling — autoscaler won't exceed this
#
# Update strategy: max_unavailable = 1 means rolling updates
#   Replace nodes one at a time — zero downtime
# ══════════════════════════════════════════════════════════════════

resource "aws_eks_node_group" "smartfinvo" {
  cluster_name    = aws_eks_cluster.smartfinvo.name
  node_group_name = "${var.cluster_name}-nodes"
  node_role_arn   = data.aws_iam_role.node.arn

  # Worker nodes go in PRIVATE subnets — not directly accessible from internet
  subnet_ids = data.aws_subnets.private.ids

  # EC2 instance size for each worker node
  instance_types = [var.node_instance_type]  # "t3.small"

  scaling_config {
    desired_size = var.node_desired_count  # 1 — normal running count
    min_size     = var.node_min_count      # 1 — never scale below this
    max_size     = var.node_max_count      # 2 — cost ceiling
  }

  # Rolling update: replace 1 node at a time (zero downtime during updates)
  update_config {
    max_unavailable = 1
  }

  depends_on = [
    aws_eks_cluster.smartfinvo,
    data.aws_iam_role.node
  ]
}