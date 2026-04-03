# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure Module Outputs
#
# Values printed after: terraform apply
# View anytime with:    terraform output
#
# These outputs save you from hunting through AWS Console for:
#   - Cluster connection details
#   - kubectl commands
#   - Resource ARNs needed by other tools
# ══════════════════════════════════════════════════════════════════

# ── EKS Cluster Outputs ───────────────────────────────────────────

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.smartfinvo.name
}

output "cluster_arn" {
  description = "Full ARN of the EKS cluster — used for IAM and cross-account access"
  value       = aws_eks_cluster.smartfinvo.arn
}

output "cluster_endpoint" {
  description = "Kubernetes API server URL — used by kubectl and automation tools"
  value       = aws_eks_cluster.smartfinvo.endpoint
}

output "cluster_version" {
  description = "Kubernetes version currently running on the cluster"
  value       = aws_eks_cluster.smartfinvo.version
}

output "cluster_ca_certificate" {
  description = "Base64-encoded cluster CA certificate — used for TLS verification"
  value       = aws_eks_cluster.smartfinvo.certificate_authority[0].data
  sensitive   = true  # Hidden from terminal output — use: terraform output -raw cluster_ca_certificate
}

# ── Node Group Outputs ────────────────────────────────────────────

output "node_group_name" {
  description = "EKS node group name"
  value       = aws_eks_node_group.smartfinvo.node_group_name
}

output "node_group_status" {
  description = "Current status of the worker node group (ACTIVE = healthy)"
  value       = aws_eks_node_group.smartfinvo.status
}

output "node_group_arn" {
  description = "Full ARN of the EKS node group"
  value       = aws_eks_node_group.smartfinvo.arn
}

# ── Connection Commands ───────────────────────────────────────────

output "kubectl_connect_command" {
  description = "Run this command to configure kubectl to talk to this cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${var.cluster_name}"
}

output "deploy_command" {
  description = "Run this command to deploy all Kubernetes manifests"
  value       = "kubectl apply -R -f ../k8s/"
}

output "get_nodes_command" {
  description = "Run this to verify worker nodes are ready"
  value       = "kubectl get nodes -o wide"
}

# ── VPC and Networking Outputs ────────────────────────────────────

output "vpc_id" {
  description = "VPC ID where EKS is deployed"
  value       = data.aws_vpc.smartfinvo.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs used by EKS worker nodes"
  value       = data.aws_subnets.private.ids
}

# ── IAM Role Outputs ──────────────────────────────────────────────

output "cluster_role_arn" {
  description = "ARN of the EKS cluster IAM role"
  value       = data.aws_iam_role.cluster.arn
}

output "node_role_arn" {
  description = "ARN of the EKS node IAM role"
  value       = data.aws_iam_role.node.arn
}

# ── Console URLs ──────────────────────────────────────────────────

output "eks_console_url" {
  description = "Direct link to this EKS cluster in AWS Console"
  value       = "https://${var.aws_region}.console.aws.amazon.com/eks/home?region=${var.aws_region}#/clusters/${var.cluster_name}"
}