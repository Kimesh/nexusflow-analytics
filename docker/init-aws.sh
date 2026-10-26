#!/usr/bin/env bash
set -eo pipefail

echo "=========================================================="
echo "⚡ Initializing NexusFlow AWS Resources in LocalStack ⚡"
echo "=========================================================="

AWS_CLI="awslocal"

# 1. Create DynamoDB Single-Table
echo "Creating DynamoDB single-table: nexusflow_events..."
$AWS_CLI dynamodb create-table \
    --table-name nexusflow_events \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
        AttributeName=GSI1PK,AttributeType=S \
        AttributeName=GSI1SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --global-secondary-indexes '[
        {
            "IndexName": "GSI1",
            "KeySchema": [
                {"AttributeName": "GSI1PK", "KeyType": "HASH"},
                {"AttributeName": "GSI1SK", "KeyType": "RANGE"}
            ],
            "Projection": {"ProjectionType": "ALL"}
        }
    ]' \
    --billing-mode PAY_PER_REQUEST || true

# 2. Create S3 Audit Archive Bucket
echo "Creating S3 bucket: nexusflow-audit-archive..."
$AWS_CLI s3 mb s3://nexusflow-audit-archive || true
$AWS_CLI s3api put-bucket-versioning \
    --bucket nexusflow-audit-archive \
    --versioning-configuration Status=Enabled || true

# 3. Create SQS Queues (DLQ and Anomaly Alerts)
echo "Creating SQS queues: nexusflow-dlq & nexusflow-anomaly-alerts..."
$AWS_CLI sqs create-queue --queue-name nexusflow-dlq || true
$AWS_CLI sqs create-queue --queue-name nexusflow-anomaly-alerts || true

echo "=========================================================="
echo "✅ LocalStack Initialization Complete! All Resources Ready"
echo "=========================================================="
