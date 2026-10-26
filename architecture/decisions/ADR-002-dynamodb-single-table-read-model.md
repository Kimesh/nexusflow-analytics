# ADR-002: CQRS & DynamoDB Single-Table Design for High-Throughput Read Projections

## Status
Accepted

## Context
High-throughput transaction analytics systems exhibit asymmetric read and write patterns:
- **Write Path:** Highly normalized, strict transactional integrity (ACID), schema-enforced validations, row-level locks, and audit trails.
- **Read Path:** Complex queries requiring fast aggregations, client transaction history timelines, real-time daily metrics, and balance looks with sub-10ms latency SLAs.

Running heavy analytical queries and timeline filters directly against primary relational OLTP tables causes severe lock contention, buffer pool churn, and degrades write throughput.

## Decision
We adopt **Command Query Responsibility Segregation (CQRS)**:
1. **Write Model (Command):** Handled exclusively by PostgreSQL 16, optimized for normalization, ACID guarantees, and high write concurrency.
2. **Read Model (Query):** Handled by **DynamoDB Local** (AWS DynamoDB in production), using a **Single-Table Design pattern**.
3. **Asynchronous Projection:** The `stream-processor` consumes events from Apache Kafka and projects denormalized views into DynamoDB in real-time.

### DynamoDB Single-Table Schema Design

| Entity / Access Pattern | Partition Key (`PK`) | Sort Key (`SK`) | Attributes / Projected Data |
| :--- | :--- | :--- | :--- |
| **Transaction Details** | `TX#<transaction_id>` | `METADATA` | `amount`, `currency`, `status`, `sender_id`, `recipient_id`, `created_at` |
| **Account Timeline** | `ACCT#<account_id>` | `TX#<timestamp>#<tx_id>` | `amount`, `type` (DEBIT/CREDIT), `balance_after`, `counterparty` |
| **Daily Account Rollup** | `ACCT#<account_id>` | `ROLLUP#<YYYY-MM-DD>` | `total_inflow`, `total_outflow`, `tx_count`, `last_updated` |
| **Global Audit Anomaly** | `AUDIT#ANOMALY` | `<risk_score>#<tx_id>` | `transaction_id`, `flag_reason`, `model_confidence`, `reviewed` |

### Global Secondary Index (`GSI1`)
- **Partition Key (`GSI1PK`):** `STATUS#<status>` (e.g., `STATUS#PENDING`, `STATUS#FLAGGED`)
- **Sort Key (`GSI1SK`):** `<timestamp>`

## Consequences
### Positive:
- **Predictable Single-Digit Millisecond Latency:** Queries retrieve complete timeline views in a single `Query` call without expensive relational joins.
- **Zero Impact on OLTP Writes:** Analytical read bursts and dashboard polling never consume PostgreSQL connections or IOPS.
- **Cost Efficiency:** Single-table architecture scales uniformly on AWS DynamoDB on-demand capacity, avoiding multiple provisioned database instances.

### Negative / Trade-offs:
- **Eventual Consistency:** Read views reflect updates within milliseconds of Kafka publication, rather than immediately within the HTTP transaction commit. The UI handles this via optimistic rendering or real-time event streaming (SSE/WebSockets).
- **Schema Rigidity:** Access patterns must be planned upfront. Ad-hoc analytics queries outside defined access patterns are routed to ClickHouse or an S3 Athena data lake.
