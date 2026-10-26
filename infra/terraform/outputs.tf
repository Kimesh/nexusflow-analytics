output "dynamodb_table_name" {
  description = "Name of the single-table DynamoDB instance"
  value       = aws_dynamodb_table.nexusflow_events.name
}

output "dynamodb_table_arn" {
  description = "ARN of the single-table DynamoDB instance"
  value       = aws_dynamodb_table.nexusflow_events.arn
}

output "s3_audit_bucket_name" {
  description = "Name of the SOC2 compliant audit archive bucket"
  value       = aws_s3_bucket.audit_archive.bucket
}

output "sqs_dlq_url" {
  description = "URL of the dead letter queue"
  value       = aws_sqs_queue.dead_letter_queue.url
}

output "sqs_anomaly_alerts_url" {
  description = "URL of the anomaly alerts queue"
  value       = aws_sqs_queue.anomaly_alerts_queue.url
}
