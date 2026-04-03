# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Cost Protection: Budget and Alerts
#
# This file sets up cost monitoring with three alert tiers:
#   80%  ($80)  → Warning email — "you're burning through budget fast"
#   100% ($100) → Alert email + IAM guardrails attached (via security.tf)
#   120% ($120) → Alert email (auto-stop action via security.tf)
#
# Alert flow:
#   AWS Budgets detects threshold → publishes to SNS topic
#   → SNS sends email to var.alert_email
#   → (optionally) triggers Budget Action to stop instances
#
# FIRST-TIME SETUP:
#   After terraform apply, AWS sends a confirmation email to
#   var.alert_email. You MUST click "Confirm subscription" or
#   you will NOT receive any alerts.
# ══════════════════════════════════════════════════════════════════

# ── SNS Topic — Email Notification Hub ────────────────────────────
# All billing alerts (budget thresholds + security events) flow
# through this single SNS topic. Both Budget and EventBridge
# (notifications.tf) publish to this topic.

resource "aws_sns_topic" "billing_alerts" {
  name = "${var.cluster_name}-billing-alerts"
  # All alert emails come from this topic
}

# Subscribe your email to the SNS topic
# AWS will send a confirmation email — you MUST click the link
# Without confirmation, you receive zero alerts

resource "aws_sns_topic_subscription" "billing_email" {
  topic_arn = aws_sns_topic.billing_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email   # "coderkali@gmail.com"
}

# ── Monthly Budget ─────────────────────────────────────────────────
# Tracks ALL AWS spend in the account for the current calendar month.
# Resets on the 1st of each month.
#
# Budget breakdown (default $100):
#   EKS control plane  = ~$73/month (always-on AWS managed service)
#   t3.small × 1 node  = ~$17/month
#   Data transfer + logs = ~$2–5/month
#   Total expected      = ~$92–95/month

resource "aws_budgets_budget" "monthly" {
  name         = "${var.cluster_name}-monthly-budget"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_limit)  # "100"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # ── Alert 1: 80% Warning ────────────────────────────────────────
  # Email at $80 — early warning, no action taken yet
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # ── Alert 2: 100% Reached ───────────────────────────────────────
  # Email at $100 — budget fully consumed
  # Also triggers the IAM guardrails action (see security.tf)
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # ── Alert 3: Forecasted to Exceed ───────────────────────────────
  # Email when AWS predicts you WILL exceed budget this month
  # Fires before you actually hit the limit — gives time to react
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}

# ── Outputs ───────────────────────────────────────────────────────

output "budget_name" {
  description = "Monthly budget name"
  value       = aws_budgets_budget.monthly.name
}

output "budget_limit" {
  description = "Monthly spend limit in USD"
  value       = "$${var.monthly_budget_limit}"
}

output "billing_alert_email" {
  description = "Email receiving billing and security alerts"
  value       = var.alert_email
}

output "billing_alert_sns_arn" {
  description = "SNS topic ARN for billing alerts — used by start.sh/stop.sh scripts"
  value       = aws_sns_topic.billing_alerts.arn
}