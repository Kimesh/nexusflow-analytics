-- ==============================================================================
-- NexusFlow Analytics: PostgreSQL Schema Definition
-- Transactional Outbox Pattern + Financial Audit Trail
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Transactions Master Table (Command Model / Write Store)
CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(128) UNIQUE,
    sender_account_id VARCHAR(64) NOT NULL,
    recipient_account_id VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    transaction_type VARCHAR(32) NOT NULL DEFAULT 'PAYMENT',
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transactions_sender ON transactions(sender_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_recipient ON transactions(recipient_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);

-- 2. Transactional Outbox Table (Guaranteed Zero-Loss Publishing)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Highly-efficient partial index for outbox polling
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- 3. Audit Log Ledger (SOC2 Compliance Ingestion Record)
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Trigger for auto-updating updated_at timestamp
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_transactions_timestamp ON transactions;
CREATE TRIGGER trg_update_transactions_timestamp
BEFORE UPDATE ON transactions
FOR EACH ROW EXECUTE FUNCTION update_timestamp();
