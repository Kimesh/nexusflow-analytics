# ADR-004: Zero-Cost Enterprise Cloud Emulation via LocalStack and Docker Compose

## Status
Accepted

## Context
Enterprise cloud architectures typically rely on AWS primitives such as:
- **Amazon DynamoDB** for sub-10ms key-value & document storage.
- **Amazon S3** for immutable audit log archiving, compliance cold storage, and Athena queries.
- **Amazon SQS / SNS** for dead-letter queues (DLQ) and asynchronous decoupling.

However, requiring a live AWS cloud subscription introduces friction:
- **Cost & Billing Risks:** Cloud resources inadvertently left running create ongoing expenses for candidates or reviewers.
- **Credential Hurdles:** Reviewers cannot easily clone and run the repo without creating IAM users, configuring access keys, and provisioning remote VPCs.
- **Reproducibility:** Network latency and region variances hinder consistent local benchmarking.

## Decision
We architect the entire platform to run **100% locally with zero cloud dependencies** while preserving identical AWS SDK integration patterns:
1. **LocalStack:** Emulates AWS services (DynamoDB, S3, SQS) on `http://localhost:4566`.
2. **Infrastructure as Code (Terraform):** Complete Terraform definitions in `/infra/terraform` configure resources against LocalStack using standard AWS providers overridden to the LocalStack endpoint.
3. **AWS SDK v2 Compatibility:** All Java and Python services consume standard AWS SDK v2 clients configured with endpoint overrides pointing to LocalStack when running locally, or AWS endpoints when deployed to production.

```
┌─────────────────────────────────────────────────────────────┐
│                      LOCAL ENVIRONMENT                      │
│                                                             │
│   Java Spring Boot API / Python Worker (AWS SDK v2)         │
│                        │                                    │
│   Endpoint Override: http://localhost:4566                  │
│                        ▼                                    │
│             [ LocalStack Container ]                        │
│             ├── DynamoDB (nexusflow_events)                 │
│             ├── S3 (nexusflow-audit-archive)                │
│             └── SQS (nexusflow-dlq)                         │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼ Zero Code Change for Production
┌─────────────────────────────────────────────────────────────┐
│                     AWS PRODUCTION CLOUD                    │
│                                                             │
│   AWS SDK automatically resolves native AWS endpoints       │
│             ├── Amazon DynamoDB                             │
│             ├── Amazon S3                                   │
│             └── Amazon SQS                                  │
└─────────────────────────────────────────────────────────────┘
```

## Consequences
### Positive:
- **Zero Financial Cost:** 100% free to run indefinitely on any developer machine.
- **Instant Client Demoability:** Prospective clients, CTOs, and recruiters can clone the repo and execute `docker compose up` without providing AWS credentials or credit cards.
- **Parity with Production:** Code uses native AWS SDK v2 interfaces (`software.amazon.awssdk` in Java and `boto3` in Python); switching from local to live AWS requires only changing environment variables (`AWS_ENDPOINT_URL`).

### Negative / Trade-offs:
- **Minor Feature Discrepancies:** LocalStack Community Edition implements core APIs but lacks certain advanced enterprise features (e.g., cross-region global tables or KMS custom hardware keystores). Core features used in NexusFlow (S3 object storage, DynamoDB single-table CRUD, SQS message handling) are fully compliant.
