# ══════════════════════════════════════════════════════════════════
# SmartFinvo — EKS Cluster and Node Group
#
# This file CREATES and MANAGES:
#   1. EKS cluster (control plane)
#   2. EKS add-ons (kube-proxy, CoreDNS etc)
#   3. Node group (EC2 worker nodes)
#
# terraform apply   → creates all of this
# terraform destroy → deletes all of this
#
# References from data.tf:
#   data.aws_vpc.smartfinvo.id
#   data.aws_subnets.private.ids
#   data.aws_iam_role.cluster.arn
#   data.aws_iam_role.node.arn
# ══════════════════════════════════════════════════════════════════

# ── EKS Cluster ───────────────────────────────────────────────────
resource "aws_eks_cluster" "smartfinvo" {
  name     = var.cluster_name        # "smartfinvo"
  version  = var.kubernetes_version  # "1.35"
  role_arn = data.aws_iam_role.cluster.arn

  # Which VPC and subnets the cluster lives in
  vpc_config {
    subnet_ids = data.aws_subnets.private.ids

    # Public and private = kubectl works from Mac
    # Node traffic stays inside VPC
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  # Authentication settings
  access_config {
    # EKS API mode — modern approach
    authentication_mode = "API"

    # Your IAM user gets cluster admin access
    bootstrap_cluster_creator_admin_permissions = true
  }

  # If AWS auto-updates the cluster version
  # Terraform should not try to revert it
  lifecycle {
    ignore_changes = [version]
  }
}

# ── EKS Add-ons ───────────────────────────────────────────────────
# Same 5 add-ons we selected manually.
# Each add-on depends on the cluster existing first.

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "coredns"

  # Wait for cluster to be ready
  depends_on = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "kube-proxy"

  depends_on = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "vpc-cni"

  depends_on = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "pod_identity" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "eks-pod-identity-agent"

  depends_on = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "metrics_server" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "metrics-server"

  depends_on = [aws_eks_cluster.smartfinvo]
}

resource "aws_eks_addon" "ebs_csi_driver" {
  cluster_name = aws_eks_cluster.smartfinvo.name
  addon_name   = "aws-ebs-csi-driver"

  depends_on = [
    aws_eks_cluster.smartfinvo,
    aws_eks_node_group.smartfinvo
  ]
}

# ── Node Group ────────────────────────────────────────────────────
# EC2 worker nodes where pods actually run.
# Created AFTER cluster is Active.

resource "aws_eks_node_group" "smartfinvo" {
  cluster_name    = aws_eks_cluster.smartfinvo.name
  node_group_name = "${var.cluster_name}-nodes"
  node_role_arn   = data.aws_iam_role.node.arn

  # Which subnets to place nodes in
  # Nodes go in PRIVATE subnets — not public
  subnet_ids = data.aws_subnets.private.ids

  # EC2 instance type for each node
  instance_types = [var.node_instance_type]  # "t3.small"

  # How many nodes to run
  scaling_config {
    desired_size = var.node_desired_count  # 2
    min_size     = var.node_min_count      # 1
    max_size     = var.node_max_count      # 3
  }

  # Rolling update — replace 1 node at a time
  # Zero downtime during node updates
  update_config {
    max_unavailable = 1
  }

  # Node group needs cluster AND node role to exist
  depends_on = [
    aws_eks_cluster.smartfinvo,
    data.aws_iam_role.node
  ]
}