# ══════════════════════════════════════════════════════════════════
# SmartFinvo — AWS Cost Protection
#
# This file sets up:
#   1. SNS topic   — sends email alerts when thresholds are hit
#   2. IAM role    — allows AWS Budgets to stop EC2 instances
#   3. Budget      — monthly spend limit with 3 alert tiers
#   4. Budget action — auto-stops EC2 nodes at 120% of budget
#
# Alert tiers:
#   80%  → warning email  (e.g. $20 of $25 spent)
#   100% → alert email    (budget reached)
#   120% → AUTO-STOP EC2 nodes + email
#
# Change var.monthly_budget_limit to set your monthly ceiling.
# ══════════════════════════════════════════════════════════════════


# ── Variables ─────────────────────────────────────────────────────

variable "monthly_budget_limit" {
  description = "Maximum monthly AWS spend in USD before auto-stop triggers"
  type        = number
  default     = 100
  # EKS control plane = ~$73/month
  # t3.small × 1 node = ~$17/month
  # Data transfer / misc = ~$2–5/month
  # Total expected = ~$92–95/month — $100 gives a safe ~$5–8 buffer
}

variable "alert_email" {
  description = "Email address to receive billing alerts"
  type        = string
  default     = "coderkali@gmail.com"
}


# ── SNS Topic (Email Notifications) ───────────────────────────────
# All budget alerts are sent through this topic

resource "aws_sns_topic" "billing_alerts" {
  name = "${var.cluster_name}-billing-alerts"
}

# Subscribe your email to the SNS topic
# AWS will send a confirmation email — you must click confirm
resource "aws_sns_topic_subscription" "billing_email" {
  topic_arn = aws_sns_topic.billing_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}


# ── IAM Role for Budget Actions ────────────────────────────────────
# AWS Budgets needs permission to stop your EC2 instances
# This role is assumed by the budgets service, not by you

resource "aws_iam_role" "budgets_action_role" {
  name = "${var.cluster_name}-budgets-action-role"

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

# Allow the role to stop EC2 instances only
# Scoped down — cannot do anything else
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
      }
    ]
  })
}


# ── Monthly Budget ─────────────────────────────────────────────────
# Tracks ALL AWS spend in the account for the current calendar month

resource "aws_budgets_budget" "monthly" {
  name         = "${var.cluster_name}-monthly-budget"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_limit)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # ── Alert 1: 80% warning ────────────────────────────────────────
  # Email when you've spent 80% of your budget
  # At $25 budget → alert fires at ~$20 spent
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # ── Alert 2: 100% reached ───────────────────────────────────────
  # Email when budget is fully consumed
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # ── Alert 3: Forecasted to exceed ───────────────────────────────
  # Email when AWS predicts you WILL exceed the budget this month
  # This fires before you actually hit the limit — early warning
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}


# ── Budget Action: Auto-Stop EC2 at 120% ──────────────────────────
# When spend exceeds 120% of budget, AWS Budgets automatically
# stops all running EC2 instances in the account.
#
# At $25 budget → kill switch fires at $30 spent
#
# NOTE: This STOPS instances (not terminates) — data is preserved.
#       Your EKS nodes will stop. Pods will go Pending.
#       Restart manually from console once you've reviewed costs.

resource "aws_budgets_budget_action" "stop_ec2" {
  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "RUN_SSM_DOCUMENTS"
  approval_model     = "AUTOMATIC"   # No manual approval needed — fires immediately
  notification_type  = "ACTUAL"

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = 120
  }

  # The IAM role that executes the stop action
  execution_role_arn = aws_iam_role.budgets_action_role.arn

  # Target: stop all EC2 instances in us-east-2
  definition {
    ssm_action_definition {
      action_sub_type = "STOP_EC2_INSTANCES"
      region          = var.aws_region
      instance_ids    = ["*"]   # All instances in the region
    }
  }

  # Notify via SNS when the action fires
  subscriber {
    address           = aws_sns_topic.billing_alerts.arn
    subscription_type = "SNS"
  }
}


# ── Outputs ───────────────────────────────────────────────────────

output "budget_name" {
  description = "Name of the monthly budget"
  value       = aws_budgets_budget.monthly.name
}

output "budget_limit" {
  description = "Monthly spend limit in USD"
  value       = "$${var.monthly_budget_limit}"
}

output "billing_alert_email" {
  description = "Email receiving billing alerts"
  value       = var.alert_email
}

output "auto_stop_threshold" {
  description = "Spend level that triggers auto-stop of EC2 nodes"
  value       = "$${var.monthly_budget_limit * 1.2} (120% of budget)"
}
