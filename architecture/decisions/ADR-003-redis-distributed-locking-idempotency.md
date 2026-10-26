# ADR-003: Redis-Backed Distributed Idempotency, Rate Limiting, and Mutual Exclusion

## Status
Accepted

## Context
In a distributed event-driven platform handling financial transactions:
1. **Network Retries & Duplicate Submissions:** Clients encountering network timeouts frequently retry POST requests. Without deduplication, duplicate transactions could be executed.
2. **At-Least-Once Consumer Delivery:** Kafka brokers guarantee at-least-once delivery. Network partitions or consumer rebalances can cause workers to receive the same event multiple times.
3. **High-Throughput Batch Reconciliation:** Multiple worker pods executing scheduled end-of-day reconciliation must not process the same batch concurrently (race conditions).
4. **Denial of Service / Noisy Neighbor Protection:** API endpoints must enforce strict client rate limits to safeguard database and queue throughput.

## Decision
We leverage **Redis 7** as an ultra-fast, distributed coordination and caching layer implementing three core patterns:

### 1. Atomic Idempotency Filter
- When an API request arrives with an `Idempotency-Key: <UUID>` header:
  - The API executes an atomic Redis command: `SETNX idempotency:<key> "PROCESSING" EX 86400`.
  - If the key already exists:
    - If status is `"PROCESSING"`: Return `409 Conflict` (request currently in flight).
    - If status is `"COMPLETED"`: Return the cached response payload immediately without hitting the transactional database.
  - Upon successful transaction commitment, the response payload is saved: `SET idempotency:<key> "<response_json>" EX 86400`.

### 2. Consumer Deduplication
- Each Kafka consumer verifies message uniqueness using an atomic Redis key `consumer:processed:<tx_id>` with a 48-hour TTL before executing projections.

### 3. Distributed Locking (Mutual Exclusion for Batch Engines)
- Scheduled reconciliation workers acquire a distributed lock via Redis with auto-expiry lease timers:
  `SET lock:reconciliation:<batch_id> <worker_node_uuid> NX PX 30000`
- Safe release is enforced using an atomic Lua script that verifies lock ownership before deletion, preventing accidental releases if a task exceeds its lease time:
  ```lua
  if redis.call("get", KEYS[1]) == ARGV[1] then
      return redis.call("del", KEYS[1])
  else
      return 0
  end
  ```

### 4. Sliding-Window / Token Bucket Rate Limiting
- Core Ingestion API enforces per-client tier limits (e.g., 500 req/sec per API key) using Redis sorted sets (`ZADD` / `ZREMRANGEBYSCORE`) or atomic token-bucket algorithms.

## Consequences
### Positive:
- **Sub-Millisecond Overhead:** In-memory Redis operations guarantee deduplication and rate limiting without introducing database I/O bottlenecks.
- **Strict Financial Guarantees:** Zero double-billing or duplicate event processing across horizontal pod replicas.
- **Safe Horizontal Scaling:** Worker pods can be scaled out dynamically across Kubernetes / Docker clusters without fear of duplicate batch processing.

### Negative / Trade-offs:
- **Redis Availability Dependency:** High availability requires Redis replication (Sentinel / Redis Cluster). For local self-hosting, Redis runs with AOF (Append-Only File) persistence enabled.
