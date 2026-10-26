# ADR-001: Transactional Outbox Pattern for Zero-Data-Loss Event Publishing

## Status
Accepted

## Context
In distributed event-driven systems, business operations frequently require updating a relational database (OLTP) and publishing an event to a message broker (Apache Kafka). 

A naive approach involves a "Dual Write":
```text
1. db.save(transaction)
2. kafkaProducer.send(event)
```
This naive approach suffers from dual-write inconsistencies:
- If Step 1 succeeds and Step 2 fails (network partition, Kafka broker unavailability), the message broker is permanently out of sync with the database.
- If Step 2 succeeds and Step 1 fails, the message broker has published phantom data that was never committed.
- Two-Phase Commit (2PC / XA transactions) introduces severe latency, distributed deadlocks, and is not natively supported by Apache Kafka.

## Decision
We implement the **Transactional Outbox Pattern**:
1. When a transaction is submitted, the business entity (`transactions`) and an outbox record (`outbox_events`) are persisted within the **same atomic ACID database transaction** in PostgreSQL.
2. A resilient Outbox Relay worker continuously extracts unpublished events from `outbox_events` and dispatches them to Apache Kafka.
3. Upon receiving a positive acknowledgment (ACK) from Kafka, the outbox record is marked as `PUBLISHED` (or removed/archived), updating the timestamp.
4. For high-throughput scenarios, CDC (Change Data Capture) via Debezium / Postgres Write-Ahead Log (WAL) can be utilized to stream outbox records without polling overhead.

## Sequence Diagram
```
Client             Core Gateway API          PostgreSQL (ACID)           Outbox Relay              Apache Kafka
  │                       │                          │                         │                         │
  │── POST /transactions ─▶                          │                         │                         │
  │                       │── BEGIN TRANSACTION ────▶│                         │                         │
  │                       │   INSERT transactions    │                         │                         │
  │                       │   INSERT outbox_events   │                         │                         │
  │                       │── COMMIT TRANSACTION ───▶│                         │                         │
  │◀── 202 Accepted ──────│                          │                         │                         │
  │                       │                          │                         │                         │
  │                       │                          │◀── Poll / Stream WAL ───│                         │
  │                       │                          │── Return unread events ─▶                         │
  │                       │                          │                         │── Publish message ─────▶│
  │                       │                          │                         │◀── Kafka ACK (ack=all) ─│
  │                       │                          │◀── UPDATE outbox status ─│                         │
```

## Consequences
### Positive:
- **Zero Data Loss Guarantee:** Guarantees at-least-once message delivery to Kafka without distributed two-phase commit overhead.
- **Resilience to Broker Outages:** If Kafka is down, the Core Gateway API can continue accepting transactions as long as PostgreSQL is healthy; outbox events will queue safely in the database and flush once Kafka recovers.
- **Auditability:** The `outbox_events` table serves as an immutable operational ledger for transaction lineage and dispute audits.

### Negative / Trade-offs:
- **At-Least-Once Delivery:** Downstream Kafka consumers may receive duplicate events if an ACK fails. Downstream consumers must implement **idempotent consumption** (addressed in [ADR-003](ADR-003-redis-distributed-locking-idempotency.md)).
- **Database Storage Growth:** The outbox table requires automated partition pruning or archival of published events.
