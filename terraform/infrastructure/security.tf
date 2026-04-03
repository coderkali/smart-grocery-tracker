# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Application Security: WAF and Budget Actions
#
# This file protects against two threat categories:
#
# THREAT 1 — Compromised AWS Credentials (account-level)
#   An attacker gets your AWS access keys and tries to spin up
#   expensive resources. Defense: budget action attaches the IAM
#   cost_guardrails deny policy (from iam.tf) at 100% spend,
#   which blocks all dangerous actions automatically.
#
# THREAT 2 — Application DDoS / Traffic Flood (app-level)
#   Someone hammers your API with thousands of requests per second,
#   causing auto-scaling and high bills. Defense: WAF rate limiting
#   blocks any IP sending more than var.waf_rate_limit req/5min.
#
# WHAT THIS FILE CREATES:
#   1. Budget action   — attaches cost guardrails policy at 100% spend
#   2. WAF Web ACL     — sits in front of ALB, inspects HTTP traffic
#   3. WAF rules       — IP reputation + rate limiting + common exploits
# ══════════════════════════════════════════════════════════════════

# ══════════════════════════════════════════════════════════════════
# Budget Action — Attach Guardrails at 100% Spend
#
# When monthly spend hits $100 (100% of budget):
#   AWS Budgets automatically attaches the cost_guardrails IAM policy
#   to your IAM user (var.iam_user_name).
#
# Effect: Even with valid credentials, no expensive actions are
# possible. EKS keeps running — this only blocks NEW resource creation.
#
# Complements the 80% and 100% email alerts in budget.tf.
# ══════════════════════════════════════════════════════════════════

resource "aws_budgets_budget_action" "apply_guardrails" {
  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"   # No manual approval needed — fires instantly
  notification_type  = "ACTUAL"      # Fires on actual spend, not forecasted

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = 100  # Trigger at 100% of budget ($100)
  }

  # The IAM role that allows Budgets to attach policies
  execution_role_arn = aws_iam_role.budgets_action_role.arn

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.cost_guardrails.arn  # from iam.tf
      users      = [var.iam_user_name]                  # "kaliprasad"
    }
  }

  # Notify via SNS when this action is triggered
  subscriber {
    address           = aws_sns_topic.billing_alerts.arn
    subscription_type = "SNS"
  }
}

# ══════════════════════════════════════════════════════════════════
# WAF Web ACL — Application Layer Protection
#
# Sits in front of your Application Load Balancer (ALB).
# Inspects every HTTP/HTTPS request before it reaches your app.
#
# Rules are evaluated in priority order (lowest number first):
#   Priority 1 — AWS IP Reputation List  (blocks known bad actors)
#   Priority 2 — Rate Limiting per IP    (blocks DDoS floods)
#   Priority 3 — Common Rule Set         (blocks SQLi, XSS, etc.)
#
# Default action: ALLOW — traffic passes unless a rule blocks it.
#
# IMPORTANT: After apply, you must attach the WAF to your ALB:
#   kubectl annotate service <your-service> \
#     service.beta.kubernetes.io/aws-load-balancer-wafv2-acl-arn=<waf_arn>
#
# Or use the output: terraform output waf_arn
# ══════════════════════════════════════════════════════════════════

resource "aws_wafv2_web_acl" "smartfinvo" {
  name  = "${var.cluster_name}-waf"
  scope = "REGIONAL"  # REGIONAL = for ALB. Use CLOUDFRONT for CloudFront distributions.

  default_action {
    allow {}  # Allow traffic unless a rule explicitly blocks it
  }

  # ── Rule 1: AWS IP Reputation List (Priority 1) ─────────────────
  # Blocks IPs that AWS has identified as malicious:
  # Tor exit nodes, botnets, scanners, known attack sources.
  # Updated continuously by AWS threat intelligence.
  rule {
    name     = "AWSManagedRulesAmazonIpReputationList"
    priority = 1

    override_action {
      none {}  # Use the rule's default action (block matching IPs)
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesAmazonIpReputationList"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesAmazonIpReputationList"
      sampled_requests_enabled   = true
    }
  }

  # ── Rule 2: Rate Limiting per IP (Priority 2) ───────────────────
  # Blocks any single IP address that sends more than
  # var.waf_rate_limit requests in any 5-minute window.
  # Blocked IPs are automatically unblocked after 5 minutes.
  #
  # 1000 req/5min = ~3.3 req/sec — enough for real users
  # A DDoS bot typically sends thousands per second → instantly blocked
  rule {
    name     = "RateLimitPerIP"
    priority = 2

    action {
      block {}  # Return HTTP 403 to the blocked IP
    }

    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit  # requests per 5 minutes
        aggregate_key_type = "IP"               # track per source IP
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitPerIP"
      sampled_requests_enabled   = true
    }
  }

  # ── Rule 3: Common Exploit Protection (Priority 3) ──────────────
  # Blocks common web application attacks:
  #   - SQL injection (SQLi)     — e.g., ' OR 1=1; DROP TABLE --
  #   - Cross-site scripting (XSS) — e.g., <script>alert(1)</script>
  #   - Path traversal           — e.g., ../../etc/passwd
  #   - HTTP protocol violations
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 3

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesCommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  # WAF-level visibility configuration
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.cluster_name}-waf"
    sampled_requests_enabled   = true
  }

  tags = {
    Name    = "${var.cluster_name}-waf"
    Project = var.cluster_name
  }
}

# ── Outputs ───────────────────────────────────────────────────────

output "waf_arn" {
  description = "WAF Web ACL ARN — attach to ALB ingress annotation: alb.ingress.kubernetes.io/wafv2-acl-arn"
  value       = aws_wafv2_web_acl.smartfinvo.arn
}

output "cost_guardrails_policy_arn" {
  description = "ARN of the IAM deny policy that blocks expensive services"
  value       = aws_iam_policy.cost_guardrails.arn
}

output "security_summary" {
  description = "Summary of all active security and cost protections"
  value = <<-EOT
    ACCOUNT PROTECTION:
      - Deny large EC2 instances (only t2.*/t3.* allowed)
      - Deny resources outside us-east-2
      - Deny RDS, Redshift, ElastiCache, SageMaker, Bedrock
      - Deny new IAM users/roles (blocks privilege escalation)
      - Auto-attach at $${var.monthly_budget_limit} spend (100% of budget)

    APPLICATION PROTECTION:
      - WAF blocks known malicious IPs (AWS reputation list)
      - WAF rate limit: ${var.waf_rate_limit} req/5min per IP → 403
      - WAF blocks SQLi, XSS, path traversal attacks

    COST KILL SWITCHES:
      - $${var.monthly_budget_limit * 0.8}  → warning email (80%)
      - $${var.monthly_budget_limit}         → email + IAM deny policy attached (100%)
      - $${var.monthly_budget_limit * 1.2}   → email + IAM guardrails active (120%)
  EOT
}