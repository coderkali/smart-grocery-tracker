#!/bin/bash
# ══════════════════════════════════════════════════════════════════
# SmartFinvo — One-Time Setup Script
# Usage: ./setup.sh
#
# Run this ONCE ever. Never need to run again.
#
# Creates permanent account-level protections that stay alive 24/7:
#   - AWS Budget ($100 limit with email alerts)
#   - IAM deny policy (blocks expensive services)
#   - SNS topic (email notification channel)
#   - WAF (rate limiting + DDoS protection)
#
# These are NOT deleted by ./stop.sh
# Cost to keep alive: ~$0/month (WAF costs ~$8/month when attached)
# ══════════════════════════════════════════════════════════════════

set -e

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║     SmartFinvo — One-Time Setup          ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "This runs ONCE. Creates permanent account protections."
echo ""

cd "$(dirname "$0")/terraform"

terraform apply \
  -target=aws_sns_topic.billing_alerts \
  -target=aws_sns_topic_subscription.billing_email \
  -target=aws_iam_role.budgets_action_role \
  -target=aws_iam_role_policy.budgets_ec2_stop \
  -target=aws_iam_role_policy.budgets_iam_attach \
  -target=aws_iam_policy.cost_guardrails \
  -target=aws_budgets_budget.monthly \
  -target=aws_budgets_budget_action.apply_guardrails \
  -target=aws_wafv2_web_acl.smartfinvo \
  -auto-approve

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║     ✅  Account protections are UP!      ║"
echo "║                                          ║"
echo "║  Budget alerts  → coderkali@gmail.com    ║"
echo "║  IAM guardrails → active                 ║"
echo "║  WAF            → active                 ║"
echo "╚══════════════════════════════════════════╝"
echo ""
echo "⚠️  Check your email and confirm the SNS subscription!"
echo "    (AWS sent a confirmation email to coderkali@gmail.com)"
echo ""
echo "You never need to run this script again."
echo "Run ./start.sh when you want to start the cluster."
echo ""
