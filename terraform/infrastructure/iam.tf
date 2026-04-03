# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure IAM Resources
#
# This file creates IAM resources for infrastructure operations:
#
# 1. Budgets Action Role (budgets_action_role)
#    Assumed by AWS Budgets service to perform automated actions:
#      - Stop EC2 instances when spend exceeds 120% of budget
#      - Attach IAM deny policy when spend exceeds 100% of budget
#
# 2. Cost Guardrails Policy (cost_guardrails)
#    Deny policy attached to your IAM user when budget is hit.
#    Blocks expensive/dangerous AWS actions to limit damage
#    from compromised credentials or runaway costs.
#
# NOTE: EKS cluster and node IAM roles are referenced via data sources
# in data.tf — they were created manually and are not managed here.
# ══════════════════════════════════════════════════════════════════

# ── Budgets Action IAM Role ───────────────────────────────────────
# AWS Budgets service needs an IAM role to perform automated actions
# like stopping EC2 instances or attaching IAM policies.
# This role is assumed by budgets.amazonaws.com, not by you.

resource "aws_iam_role" "budgets_action_role" {
  name = "${var.cluster_name}-budgets-action-role"

  # Trust policy: only the AWS Budgets service can assume this role
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "budgets.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

# ── Budgets Policy 1: EC2 Stop Permission ─────────────────────────
# Allows AWS Budgets to stop (not terminate) running EC2 instances.
# Stopping = preserves data, you can restart. Terminating = deleted.
# Triggered at 120% of monthly budget (see budget.tf).

resource "aws_iam_role_policy" "budgets_ec2_stop" {
  name = "${var.cluster_name}-budgets-ec2-stop-policy"
  role = aws_iam_role.budgets_action_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ec2:StopInstances",
          "ec2:DescribeInstances",
          "ec2:DescribeInstanceStatus"
        ]
        Resource = "*"
        # Describe actions require * — they don't support resource-level restrictions
      }
    ]
  })
}

# ── Budgets Policy 2: IAM Policy Attach Permission ────────────────
# Allows AWS Budgets to attach/detach IAM policies to users.
# Used to attach the cost_guardrails policy at 100% budget spend.
# See security.tf for the budget action that triggers this.

resource "aws_iam_role_policy" "budgets_iam_attach" {
  name = "${var.cluster_name}-budgets-iam-attach-policy"
  role = aws_iam_role.budgets_action_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "iam:AttachUserPolicy",
          "iam:DetachUserPolicy",
          "iam:AttachRolePolicy",
          "iam:DetachRolePolicy"
        ]
        Resource = "*"
      }
    ]
  })
}

# ══════════════════════════════════════════════════════════════════
# Cost Guardrails IAM Policy
#
# This DENY policy is automatically attached to your IAM user when
# monthly spend hits 100% of the budget (see security.tf).
#
# Once attached, even if someone has your AWS credentials, they
# CANNOT create expensive resources — all attempts are denied.
#
# The policy blocks:
#   - Large EC2 instances (only t2.* and t3.* allowed)
#   - Resources outside us-east-2 region
#   - Expensive databases (RDS, Redshift, ElastiCache)
#   - AI/ML services (SageMaker, Bedrock — cost hundreds/hour)
#   - Privilege escalation (creating new IAM users/roles)
#   - Data exfiltration services (Glacier, DataSync, Transfer)
#
# IMPORTANT: This is a DENY policy — it overrides Allow policies.
# The EKS cluster will still run (it doesn't use denied actions).
# ══════════════════════════════════════════════════════════════════

resource "aws_iam_policy" "cost_guardrails" {
  name        = "${var.cluster_name}-cost-guardrails"
  description = "Deny expensive services and large instance types to prevent runaway costs"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [

      # ── Block large/expensive EC2 instance types ──────────────
      # Allows t2.*, t3.* only
      # Blocks: g4dn (GPU), p3 (ML), r5 (memory), m5 (general) etc.
      {
        Sid    = "DenyLargeEC2Instances"
        Effect = "Deny"
        Action = ["ec2:RunInstances"]
        Resource = "arn:aws:ec2:*:*:instance/*"
        Condition = {
          StringNotLike = {
            "ec2:InstanceType" = ["t2.*", "t3.*"]
          }
        }
      },

      # ── Block resources outside us-east-2 ────────────────────
      # Prevents creating resources in regions you don't monitor
      # or that have different pricing (some regions are more expensive)
      {
        Sid    = "DenyNonApprovedRegions"
        Effect = "Deny"
        Action = [
          "ec2:*",
          "rds:*",
          "eks:*",
          "elasticache:*",
          "s3:CreateBucket"
        ]
        Resource = "*"
        Condition = {
          StringNotEquals = {
            "aws:RequestedRegion" = ["us-east-2"]
          }
        }
      },

      # ── Block expensive managed database services ──────────────
      # RDS = starts at ~$20/month, can reach hundreds
      # Redshift = $0.25/node/hour
      # ElastiCache = $0.017/hour per node
      # DAX = $0.269/hour per node
      {
        Sid    = "DenyExpensiveDatabases"
        Effect = "Deny"
        Action = [
          "rds:CreateDBInstance",
          "rds:CreateDBCluster",
          "redshift:CreateCluster",
          "elasticache:CreateCacheCluster",
          "elasticache:CreateReplicationGroup",
          "dax:CreateCluster"
        ]
        Resource = "*"
      },

      # ── Block AI/ML services (extremely expensive) ────────────
      # SageMaker training: $0.50–$32/hour per instance type
      # Bedrock: charged per token — can spike with runaway jobs
      # Rekognition, Comprehend: per API call — can add up fast
      {
        Sid    = "DenyMLServices"
        Effect = "Deny"
        Action = [
          "sagemaker:CreateTrainingJob",
          "sagemaker:CreateEndpoint",
          "sagemaker:CreateNotebookInstance",
          "bedrock:*",
          "comprehend:*",
          "rekognition:*"
        ]
        Resource = "*"
      },

      # ── Block privilege escalation ────────────────────────────
      # Prevents an attacker from creating new admin users or
      # attaching powerful policies to escape these restrictions
      {
        Sid    = "DenyPrivilegeEscalation"
        Effect = "Deny"
        Action = [
          "iam:CreateUser",
          "iam:CreateRole",
          "iam:AttachUserPolicy",
          "iam:AttachRolePolicy",
          "iam:PutUserPolicy",
          "iam:PutRolePolicy",
          "iam:CreateAccessKey"
        ]
        Resource = "*"
      },

      # ── Block data exfiltration services ──────────────────────
      # Prevents an attacker from moving your data out of AWS
      # using archival, sync, or file transfer services
      {
        Sid    = "DenyDataExfiltration"
        Effect = "Deny"
        Action = [
          "glacier:CreateVault",
          "datasync:*",
          "transfer:*",
          "snowball:*"
        ]
        Resource = "*"
      }
    ]
  })
}