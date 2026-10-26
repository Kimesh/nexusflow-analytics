resource "aws_sqs_queue" "dead_letter_queue" {
  name                      = "nexusflow-dlq"
  message_retention_seconds = 1209600 # 14 days
  visibility_timeout_seconds = 300

  tags = {
    Environment = var.environment
    Project     = "NexusFlow-Analytics"
    Purpose     = "Poison-Message-Audit"
  }
}

resource "aws_sqs_queue" "anomaly_alerts_queue" {
  name                      = "nexusflow-anomaly-alerts"
  message_retention_seconds = 345600 # 4 days
  visibility_timeout_seconds = 60

  tags = {
    Environment = var.environment
    Project     = "NexusFlow-Analytics"
    Purpose     = "Real-time-Security-Alerts"
  }
}
