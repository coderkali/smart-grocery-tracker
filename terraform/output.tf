# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Terraform Outputs
#
# Values printed in terminal after terraform apply completes.
# Saves you from going to AWS Console to find these.
#
# View outputs anytime with: terraform output
# ══════════════════════════════════════════════════════════════════

output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.smartfinvo.name
}

output "cluster_endpoint" {
  description = "Kubernetes API server endpoint"
  value       = aws_eks_cluster.smartfinvo.endpoint
}

output "cluster_arn" {
  description = "Full ARN of the EKS cluster"
  value       = aws_eks_cluster.smartfinvo.arn
}

output "cluster_version" {
  description = "Kubernetes version running on the cluster"
  value       = aws_eks_cluster.smartfinvo.version
}

output "node_group_status" {
  description = "Status of the worker node group"
  value       = aws_eks_node_group.smartfinvo.status
}

output "kubectl_connect_command" {
  description = "Run this command to connect kubectl to the cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${var.cluster_name}"
}

output "deploy_command" {
  description = "Run this command to deploy k8s manifests"
  value       = "kubectl apply -R -f ../k8s/"
}

output "billing_alert_sns_arn" {
  description = "SNS topic ARN used by start.sh and stop.sh for email notifications"
  value       = aws_sns_topic.billing_alerts.arn
}