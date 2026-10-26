"""
NexusFlow SQS Security Alert Dispatcher
Publishes high-severity anomaly alerts to LocalStack SQS.
"""
import os
import json
import logging
import boto3

logger = logging.getLogger("nexusflow.sqs_notifier")

class SQSAlertNotifier:
    def __init__(self):
        self.queue_url = os.getenv("SQS_ALERT_QUEUE", "http://localhost:4566/000000000000/nexusflow-anomaly-alerts")
        endpoint_url = os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566")
        region = os.getenv("AWS_REGION", "us-east-1")
        access_key = os.getenv("AWS_ACCESS_KEY_ID", "mock_access_key")
        secret_key = os.getenv("AWS_SECRET_ACCESS_KEY", "mock_secret_key")

        self.sqs_client = boto3.client(
            "sqs",
            endpoint_url=endpoint_url,
            region_name=region,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key
        )

    def dispatch_alert(self, alert_payload: dict):
        try:
            self.sqs_client.send_message(
                QueueUrl=self.queue_url,
                MessageBody=json.dumps(alert_payload, default=str)
            )
            logger.info(f"Dispatched high-severity alert for tx {alert_payload.get('transaction_id')} to SQS")
        except Exception as e:
            logger.error(f"Failed to dispatch alert to SQS: {e}")
