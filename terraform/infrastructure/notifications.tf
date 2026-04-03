# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Infrastructure Change Notifications
#
# Sends real-time email alerts when critical AWS resources change:
#   - EKS cluster is created or deleted
#   - EKS node group is created or deleted
#   - EC2 instances are launched, stopped, or terminated
#   - Expensive services (RDS, SageMaker, etc.) are created
#
# Alert flow:
#   AWS Action → CloudTrail logs it → EventBridge matches pattern
#   → Publishes formatted message to SNS topic (billing_alerts)
#   → SNS sends email to var.alert_email
#
# This catches changes from ANY method: Terraform, AWS Console,
# AWS CLI, SDK, or an attacker with stolen credentials.
# Alert arrives within seconds of the action occurring.
# ══════════════════════════════════════════════════════════════════

# ── Allow EventBridge to publish to our SNS topic ─────────────────
# SNS topics block external publishers by default.
# This policy grants EventBridge permission to publish alerts
# through the billing_alerts SNS topic (created in budget.tf).

resource "aws_sns_topic_policy" "allow_eventbridge" {
  arn = aws_sns_topic.billing_alerts.arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEventBridgePublish"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action   = "sns:Publish"
        Resource = aws_sns_topic.billing_alerts.arn
      }
    ]
  })
}

# ══════════════════════════════════════════════════════════════════
# Rule 1: EKS Cluster Created or Deleted
#
# Fires when CreateCluster or DeleteCluster is called via any method.
# An unexpected DeleteCluster is a critical incident — your entire
# application goes offline and data may be lost.
# ══════════════════════════════════════════════════════════════════

resource "aws_cloudwatch_event_rule" "eks_cluster_changes" {
  name        = "${var.cluster_name}-eks-cluster-changes"
  description = "Alert when EKS cluster is created or deleted"

  event_pattern = jsonencode({
    source      = ["aws.eks"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["eks.amazonaws.com"]
      eventName   = ["CreateCluster", "DeleteCluster"]
    }
  })
}

resource "aws_cloudwatch_event_target" "eks_cluster_changes_sns" {
  rule      = aws_cloudwatch_event_rule.eks_cluster_changes.name
  target_id = "SendToSNS"
  arn       = aws_sns_topic.billing_alerts.arn

  input_transformer {
    input_paths = {
      account = "$.account"
      region  = "$.region"
      event   = "$.detail.eventName"
      user    = "$.detail.userIdentity.arn"
      cluster = "$.detail.requestParameters.name"
      time    = "$.time"
    }
    input_template = <<EOF
"SmartFinvo Alert — EKS Cluster Change Detected

Event:   <event>
Cluster: <cluster>
Region:  <region>
Account: <account>
By:      <user>
Time:    <time>

Login to AWS Console to verify this was you."
EOF
  }
}

# ══════════════════════════════════════════════════════════════════
# Rule 2: EKS Node Group Created or Deleted
#
# Fires when CreateNodegroup or DeleteNodegroup is called.
# Unexpected node group deletion means all pods lose their compute.
# Unexpected creation means someone may be adding unmonitored capacity.
# ══════════════════════════════════════════════════════════════════

resource "aws_cloudwatch_event_rule" "eks_nodegroup_changes" {
  name        = "${var.cluster_name}-eks-nodegroup-changes"
  description = "Alert when EKS node group is created or deleted"

  event_pattern = jsonencode({
    source      = ["aws.eks"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["eks.amazonaws.com"]
      eventName   = ["CreateNodegroup", "DeleteNodegroup"]
    }
  })
}

resource "aws_cloudwatch_event_target" "eks_nodegroup_changes_sns" {
  rule      = aws_cloudwatch_event_rule.eks_nodegroup_changes.name
  target_id = "SendToSNS"
  arn       = aws_sns_topic.billing_alerts.arn

  input_transformer {
    input_paths = {
      account   = "$.account"
      region    = "$.region"
      event     = "$.detail.eventName"
      user      = "$.detail.userIdentity.arn"
      nodegroup = "$.detail.requestParameters.nodegroupName"
      time      = "$.time"
    }
    input_template = <<EOF
"SmartFinvo Alert — EKS Node Group Change Detected

Event:      <event>
Node Group: <nodegroup>
Region:     <region>
Account:    <account>
By:         <user>
Time:       <time>

Login to AWS Console to verify this was you."
EOF
  }
}

# ══════════════════════════════════════════════════════════════════
# Rule 3: EC2 Instance Launched or Terminated
#
# Catches any new EC2 instance creation — even from the console.
# EKS worker nodes will trigger this during normal scaling.
# Alert if you see RunInstances you didn't expect — it may indicate
# compromised credentials being used to mine cryptocurrency.
# ══════════════════════════════════════════════════════════════════

resource "aws_cloudwatch_event_rule" "ec2_instance_changes" {
  name        = "${var.cluster_name}-ec2-instance-changes"
  description = "Alert when EC2 instances are launched, stopped, or terminated"

  event_pattern = jsonencode({
    source      = ["aws.ec2"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["ec2.amazonaws.com"]
      eventName   = ["RunInstances", "StopInstances", "TerminateInstances"]
    }
  })
}

resource "aws_cloudwatch_event_target" "ec2_instance_changes_sns" {
  rule      = aws_cloudwatch_event_rule.ec2_instance_changes.name
  target_id = "SendToSNS"
  arn       = aws_sns_topic.billing_alerts.arn

  input_transformer {
    input_paths = {
      account = "$.account"
      region  = "$.region"
      event   = "$.detail.eventName"
      user    = "$.detail.userIdentity.arn"
      time    = "$.time"
    }
    input_template = <<EOF
"SmartFinvo Alert — EC2 Instance Change Detected

Event:   <event>
Region:  <region>
Account: <account>
By:      <user>
Time:    <time>

Login to AWS Console → EC2 to verify this was you."
EOF
  }
}

# ══════════════════════════════════════════════════════════════════
# Rule 4: Expensive Services Created
#
# Catches attempts to create RDS, Redshift, SageMaker, ElastiCache.
# These are also blocked by the IAM guardrails policy (iam.tf), but
# if guardrails aren't yet attached, this alert gives you a chance
# to manually delete the resource before it accrues large charges.
# ══════════════════════════════════════════════════════════════════

resource "aws_cloudwatch_event_rule" "expensive_service_created" {
  name        = "${var.cluster_name}-expensive-service-alert"
  description = "Alert when expensive managed services are created"

  event_pattern = jsonencode({
    source      = ["aws.rds", "aws.redshift", "aws.sagemaker", "aws.elasticache"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventName = [
        "CreateDBInstance",
        "CreateDBCluster",
        "CreateCluster",
        "CreateNotebookInstance",
        "CreateCacheCluster"
      ]
    }
  })
}

resource "aws_cloudwatch_event_target" "expensive_service_created_sns" {
  rule      = aws_cloudwatch_event_rule.expensive_service_created.name
  target_id = "SendToSNS"
  arn       = aws_sns_topic.billing_alerts.arn

  input_transformer {
    input_paths = {
      account = "$.account"
      region  = "$.region"
      event   = "$.detail.eventName"
      user    = "$.detail.userIdentity.arn"
      source  = "$.source"
      time    = "$.time"
    }
    input_template = <<EOF
"SECURITY ALERT — Expensive Service Created!

Service: <source>
Event:   <event>
Region:  <region>
Account: <account>
By:      <user>
Time:    <time>

This may be UNAUTHORIZED. Login to AWS Console immediately and investigate!"
EOF
  }
}