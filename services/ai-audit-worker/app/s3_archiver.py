"""
NexusFlow S3 Compliance Archiver
Archives immutable audit records to LocalStack S3 partitioned by date.
"""
import os
import json
import logging
from datetime import datetime
import boto3
from botocore.config import Config

logger = logging.getLogger("nexusflow.s3_archiver")

class S3AuditArchiver:
    def __init__(self):
        self.bucket = os.getenv("S3_AUDIT_BUCKET", "nexusflow-audit-archive")
        endpoint_url = os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566")
        region = os.getenv("AWS_REGION", "us-east-1")
        access_key = os.getenv("AWS_ACCESS_KEY_ID", "mock_access_key")
        secret_key = os.getenv("AWS_SECRET_ACCESS_KEY", "mock_secret_key")

        self.s3_client = boto3.client(
            "s3",
            endpoint_url=endpoint_url,
            region_name=region,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            config=Config(s3={"addressing_style": "path"})
        )

    def archive_record(self, record: dict):
        try:
            now = datetime.utcnow()
            key = f"audit/{now.strftime('%Y/%m/%d')}/{record.get('id', 'unknown')}.json"
            body = json.dumps(record, default=str)

            self.s3_client.put_object(
                Bucket=self.bucket,
                Key=key,
                Body=body.encode("utf-8"),
                ContentType="application/json"
            )
            logger.debug(f"Archived audit object to s3://{self.bucket}/{key}")
        except Exception as ex:
            logger.error(f"S3 archival error: {ex}")
