# ══════════════════════════════════════════════════════════════════
# SmartFinvo — Resource Change Notifications
#
# Sends email to coderkali@gmail.com whenever:
#   - EKS cluster is created or deleted
#   - EKS node group is created or deleted
#   - EC2 instance is launched, stopped or terminated
#   - Any large/expensive resource is created
#
# How it works:
#   AWS Action → CloudTrail logs it → EventBridge detects it
#   → SNS topic → Email to you
#
# This catches EVERYTHING — scripts, console, CLI, anyone.
# If a hacker creates a resource, you get an email within seconds.
# ══════════════════════════════════════════════════════════════════


# ── Allow EventBridge to publish to our SNS topic ─────────────────
# By default SNS blocks outside services from publishing.
# This policy allows EventBridge (events.amazonaws.com) to send
# notifications through our existing billing_alerts SNS topic.

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


# ── EventBridge Rule 1: EKS Cluster Created or Deleted ────────────
# Fires when someone runs CreateCluster or DeleteCluster via
# any method — Terraform, AWS console, CLI, or API.

resource "aws_cloudwatch_event_rule" "eks_cluster_changes" {
  name        = "${var.cluster_name}-eks-cluster-changes"
  description = "Notify when EKS cluster is created or deleted"

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

  # Format the email message to be readable
  input_transformer {
    input_paths = {
      account   = "$.account"
      region    = "$.region"
      event     = "$.detail.eventName"
      user      = "$.detail.userIdentity.arn"
      cluster   = "$.detail.requestParameters.name"
      time      = "$.time"
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


# ── EventBridge Rule 2: EKS Node Group Created or Deleted ─────────

resource "aws_cloudwatch_event_rule" "eks_nodegroup_changes" {
  name        = "${var.cluster_name}-eks-nodegroup-changes"
  description = "Notify when EKS node group is created or deleted"

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


# ── EventBridge Rule 3: EC2 Instance Launched or Terminated ───────
# Catches any new EC2 instance spin-up — even from the console.
# Critical for catching compromised credentials.

resource "aws_cloudwatch_event_rule" "ec2_instance_changes" {
  name        = "${var.cluster_name}-ec2-instance-changes"
  description = "Notify when EC2 instances are launched, stopped or terminated"

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


# ── EventBridge Rule 4: Expensive Services Created ────────────────
# Catches if anyone creates RDS, Redshift, SageMaker etc.
# These are the services blocked by IAM guardrails —
# but if guardrails are bypassed, you still get notified.

resource "aws_cloudwatch_event_rule" "expensive_service_created" {
  name        = "${var.cluster_name}-expensive-service-alert"
  description = "Notify when expensive services are created"

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
"⚠️ SmartFinvo SECURITY ALERT — Expensive Service Created!

Service: <source>
Event:   <event>
Region:  <region>
Account: <account>
By:      <user>
Time:    <time>

This may be UNAUTHORIZED. Login to AWS Console immediately and check!"
EOF
  }
}
