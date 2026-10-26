"""
NexusFlow AI Audit Worker & Compliance Stream Inspector
FastAPI Application running async Kafka consumer and real-time anomaly detection.
"""
import asyncio
import json
import logging
import os
from contextlib import asynccontextmanager
from typing import Dict, Any, List
from collections import deque

from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import Counter, make_asgi_app
import uvicorn

from app.detector import AnomalyDetector
from app.genai_summarizer import GenAISummarizer
from app.s3_archiver import S3AuditArchiver
from app.sqs_notifier import SQSAlertNotifier

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("nexusflow.audit_main")

# Prometheus Metrics
ANOMALIES_COUNTER = Counter("nexusflow_anomalies_detected_total", "Total anomalies detected by AI worker")
AUDIT_COUNTER = Counter("nexusflow_ai_audits_total", "Total transactions analyzed by AI engine")

# In-memory recent anomalies ring buffer (last 50 items)
recent_anomalies = deque(maxlen=50)

detector = AnomalyDetector(threshold_amount=float(os.getenv("ANOMALY_THRESHOLD_AMOUNT", "10000.0")))
summarizer = GenAISummarizer()
s3_archiver = S3AuditArchiver()
sqs_notifier = SQSAlertNotifier()

async def kafka_consumer_loop():
    from aiokafka import AIOKafkaConsumer

    bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    logger.info(f"Connecting to Kafka brokers at {bootstrap_servers}...")

    # Wait for Kafka to become available
    retries = 15
    consumer = None
    while retries > 0:
        try:
            consumer = AIOKafkaConsumer(
                "transaction.events",
                bootstrap_servers=bootstrap_servers,
                group_id="nexusflow-ai-audit-group",
                auto_offset_reset="earliest",
                enable_auto_commit=True
            )
            await consumer.start()
            logger.info("AIOKafkaConsumer successfully started and subscribed to transaction.events")
            break
        except Exception as e:
            logger.warning(f"Kafka connection attempt failed ({retries} left): {e}. Retrying in 4s...")
            retries -= 1
            await asyncio.sleep(4)

    if not consumer:
        logger.error("Could not connect to Kafka. Consumer loop aborting.")
        return

    try:
        async for msg in consumer:
            try:
                payload = json.loads(msg.value.decode("utf-8"))
                AUDIT_COUNTER.inc()

                is_anomaly, risk_score, reason = detector.analyze_transaction(payload)

                if is_anomaly:
                    ANOMALIES_COUNTER.inc()
                    brief = await summarizer.generate_incident_brief(payload, risk_score, reason)

                    record = {
                        "transaction_id": payload.get("id"),
                        "amount": payload.get("amount"),
                        "currency": payload.get("currency", "USD"),
                        "sender": payload.get("senderAccountId"),
                        "recipient": payload.get("recipientAccountId"),
                        "risk_score": risk_score,
                        "violation_reason": reason,
                        "ai_narrative": brief,
                        "timestamp": payload.get("createdAt")
                    }

                    recent_anomalies.appendleft(record)
                    logger.warning(f"🚨 ANOMALY FLAGGED: Tx {record['transaction_id']} (Score: {risk_score})")

                    # Archive to S3 & SQS alert
                    s3_archiver.archive_record(record)
                    sqs_notifier.dispatch_alert(record)

            except Exception as ex:
                logger.error(f"Error parsing transaction stream record: {ex}")
    finally:
        await consumer.stop()

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Start Kafka consumer background task
    consumer_task = asyncio.create_task(kafka_consumer_loop())
    yield
    # Shutdown
    consumer_task.cancel()

app = FastAPI(
    title="NexusFlow AI Audit Worker",
    version="1.0.0",
    description="Real-time compliance monitoring, anomaly detection, and LLM audit generation",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount Prometheus metrics endpoint
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)

@app.get("/health")
def health_check():
    return {"status": "HEALTHY", "service": "ai-audit-worker"}

@app.get("/api/v1/audit/anomalies", response_model=List[Dict[str, Any]])
def get_recent_anomalies():
    """Returns the most recent anomalies flagged by the AI engine."""
    return list(recent_anomalies)

@app.post("/api/v1/audit/analyze")
async def analyze_single_transaction(tx: Dict[str, Any]):
    """Manually test anomaly detection & LLM summarization on an arbitrary transaction payload."""
    is_anomaly, risk_score, reason = detector.analyze_transaction(tx)
    brief = await summarizer.generate_incident_brief(tx, risk_score, reason) if is_anomaly else "Transaction complies with standard risk policies."

    return {
        "is_anomaly": is_anomaly,
        "risk_score": risk_score,
        "violation_reason": reason,
        "ai_narrative": brief
    }

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=False)
