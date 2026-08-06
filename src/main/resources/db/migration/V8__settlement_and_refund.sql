-- V8: Settlement batch processing and refund system

-- 1. Extend settlements table
ALTER TABLE settlements ADD COLUMN batch_id VARCHAR(50);
ALTER TABLE settlements ADD COLUMN processed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE settlements ADD COLUMN failure_reason VARCHAR(500);

-- Update settlement status constraint to include new states
ALTER TABLE settlements DROP CONSTRAINT IF EXISTS chk_settlement_status;
ALTER TABLE settlements DROP CONSTRAINT IF EXISTS settlements_status_check;
ALTER TABLE settlements ADD CONSTRAINT chk_settlement_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

-- Migrate existing SETTLED records to COMPLETED
UPDATE settlements SET status = 'COMPLETED', processed_at = settled_at WHERE status = 'SETTLED';

-- 2. Create refunds table
CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    refunded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_refund_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_refunds_order ON refunds(order_id);
CREATE INDEX idx_refunds_status ON refunds(status);

-- 3. Create settlement batches table
CREATE TABLE settlement_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_batch_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- 4. Add index on settlements batch_id
CREATE INDEX idx_settlements_batch ON settlements(batch_id);

-- 5. Add updated_at trigger for refunds
CREATE OR REPLACE TRIGGER set_refunds_updated_at
    BEFORE UPDATE ON refunds
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 6. Update payments status constraint to include REFUNDED
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_status;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('SUCCEEDED', 'REFUNDED'));
