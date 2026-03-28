# ══════════════════════════════════════════════════════════════════
# SmartFinvo — AWS Security & Cost Abuse Protection
#
# Protects against two threats:
#
# THREAT 1 — Compromised AWS credentials
#   Someone gets your AWS keys and launches expensive resources.
#   Fix: IAM deny policy blocks expensive services + enforces
#        us-east-2 only + blocks large instance types.
#
# THREAT 2 — Application DDoS / traffic flood
#   Someone hammers your app, triggering auto-scale and high bills.
#   Fix: WAF rate limiting blocks IPs that send too many requests.
#        Kubernetes HPA max cap prevents unbounded node scaling.
#
# WHAT THIS FILE CREATES:
#   1. IAM deny policy     — blocks expensive/dangerous AWS actions
#   2. Budget action       — attaches deny policy when spend > 100%
#   3. WAF web ACL         — rate limits per IP on the load balancer
#   4. WAF managed rules   — blocks known malicious IPs/bots
# ══════════════════════════════════════════════════════════════════


# ── Variables ─────────────────────────────────────────────────────

variable "waf_rate_limit" {
  description = "Max requests per 5 minutes per IP before WAF blocks it"
  type        = number
  default     = 1000
  # 1000 req/5min = ~3.3 req/sec per IP — enough for real users
  # A DDoS bot typically sends thousands per second
  # Lower to 300 if you want stricter protection
}

variable "iam_user_name" {
  description = "Your AWS IAM username — the policy will be attached here at 100% budget"
  type        = string
  default     = "kaliprasad"
  # Run: aws iam get-user --query 'User.UserName' to confirm yours
}


# ══════════════════════════════════════════════════════════════════
# THREAT 1 — Compromised AWS Credentials
# ══════════════════════════════════════════════════════════════════

# ── IAM Deny Policy ───────────────────────────────────────────────
# This policy DENIES:
#   - Launching any EC2 instance larger than t3.medium
#   - Creating expensive managed services (RDS, Redshift, SageMaker etc)
#   - Creating resources outside us-east-2
#   - Creating new IAM users/roles (prevents privilege escalation)
#
# Even if someone has your AWS keys, they cannot spin up
# GPU instances, databases, or ML workloads.

resource "aws_iam_policy" "cost_guardrails" {
  name        = "${var.cluster_name}-cost-guardrails"
  description = "Denies expensive services and large instance types to prevent runaway costs"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [

      # ── Block large/expensive EC2 instance types ────────────────
      # Allows t2.*, t3.* only — blocks g4dn, p3, r5, m5 etc
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

      # ── Block resources outside us-east-2 ──────────────────────
      # Prevents spinning up resources in expensive regions
      # or regions you don't monitor
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

      # ── Block expensive managed database services ───────────────
      # RDS, Redshift, ElastiCache, DynamoDB on-demand can get
      # very expensive very fast if spun up by an attacker
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

      # ── Block AI/ML services (extremely expensive) ──────────────
      # SageMaker training jobs, Bedrock, etc. can cost
      # hundreds of dollars per hour
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

      # ── Block privilege escalation ──────────────────────────────
      # Prevents an attacker from creating new admin users/roles
      # or attaching powerful policies to escape restrictions
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

      # ── Block data exfiltration services ───────────────────────
      # Prevents attacker from moving data out using
      # Glacier, DataSync, Transfer etc.
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

# ── Budget Action: Apply Deny Policy at 100% spend ────────────────
# When monthly spend hits $100, AWS Budgets automatically
# attaches the cost_guardrails policy to your IAM user.
# This locks down the account while still keeping EKS running.
#
# Complements the existing 120% auto-stop action in budget.tf:
#   100% ($100) → attach IAM deny policy   ← this action
#   120% ($120) → stop all EC2 nodes       ← from budget.tf

resource "aws_budgets_budget_action" "apply_guardrails" {
  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = 100
  }

  execution_role_arn = aws_iam_role.budgets_action_role.arn

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.cost_guardrails.arn
      users      = [var.iam_user_name]
    }
  }

  subscriber {
    address           = aws_sns_topic.billing_alerts.arn
    subscription_type = "SNS"
  }
}

# Also give the budgets role permission to attach IAM policies
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
# THREAT 2 — Application DDoS / Traffic Flood
# ══════════════════════════════════════════════════════════════════

# ── WAF Web ACL ───────────────────────────────────────────────────
# Sits in front of your load balancer.
# Inspects every HTTP request before it reaches your Spring Boot app.
#
# Rules applied in order (lowest priority number runs first):
#   1. AWS Managed — blocks known bad IPs, scanners, botnets
#   2. Rate limit   — blocks IPs sending > 1000 req/5min
#   3. AWS Managed — blocks common exploits (SQLi, XSS etc)

resource "aws_wafv2_web_acl" "smartfinvo" {
  name  = "${var.cluster_name}-waf"
  scope = "REGIONAL" # REGIONAL = ALB. Use CLOUDFRONT for CloudFront.

  default_action {
    allow {} # Allow all traffic unless a rule blocks it
  }

  # ── Rule 1: AWS IP Reputation List ─────────────────────────────
  # Blocks IPs that AWS has identified as malicious:
  # botnets, scanners, Tor exit nodes, known attackers
  rule {
    name     = "AWSManagedRulesAmazonIpReputationList"
    priority = 1

    override_action {
      none {} # Use the rule's default action (block)
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

  # ── Rule 2: Rate Limiting per IP ───────────────────────────────
  # Blocks any single IP that sends more than 1000 requests
  # in any 5-minute window.
  # Blocked IP is automatically unblocked after 5 minutes.
  # This stops DDoS floods and scraping bots.
  rule {
    name     = "RateLimitPerIP"
    priority = 2

    action {
      block {} # Block the request — return 403
    }

    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit # 1000 req per 5 min
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitPerIP"
      sampled_requests_enabled   = true
    }
  }

  # ── Rule 3: Common Exploit Protection ──────────────────────────
  # Blocks common web exploits: SQL injection, XSS, path traversal
  # Protects your Spring Boot API from web attacks
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

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.cluster_name}-waf"
    sampled_requests_enabled   = true
  }

  tags = {
    Name    = "${var.cluster_name}-waf"
    Project = "smartfinvo"
  }
}


# ── Outputs ───────────────────────────────────────────────────────

output "waf_arn" {
  description = "WAF Web ACL ARN — attach this to your ALB ingress annotation"
  value       = aws_wafv2_web_acl.smartfinvo.arn
}

output "cost_guardrails_policy_arn" {
  description = "IAM policy ARN that blocks expensive services"
  value       = aws_iam_policy.cost_guardrails.arn
}

output "security_summary" {
  description = "Summary of active protections"
  value = <<-EOT
    ACCOUNT PROTECTION:
      - Deny large EC2 instances (only t2.*/t3.* allowed)
      - Deny resources outside us-east-2
      - Deny RDS, Redshift, ElastiCache, SageMaker, Bedrock
      - Deny new IAM users/roles (blocks privilege escalation)
      - Auto-attach at $100 spend via Budget Action

    APPLICATION PROTECTION:
      - WAF blocks known malicious IPs (AWS reputation list)
      - WAF rate limit: ${var.waf_rate_limit} req/5min per IP → 403
      - WAF blocks SQLi, XSS, path traversal attacks

    COST KILL SWITCHES:
      - $80  → warning email
      - $100 → email + IAM deny policy attached
      - $120 → email + all EC2 nodes stopped
  EOT
}
