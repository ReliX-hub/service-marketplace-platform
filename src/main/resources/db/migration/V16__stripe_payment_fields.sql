-- Asynchronous payment-provider fields and idempotent Stripe webhook storage.

ALTER TABLE payments ADD COLUMN payment_intent_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN charge_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN provider_status VARCHAR(40);
ALTER TABLE payments ADD COLUMN failure_message VARCHAR(500);
ALTER TABLE payments ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE payments ADD COLUMN client_secret VARCHAR(255);
ALTER TABLE payments ADD CONSTRAINT uk_payments_payment_intent UNIQUE (payment_intent_id);
CREATE UNIQUE INDEX uk_payments_charge_id
    ON payments(charge_id)
    WHERE charge_id IS NOT NULL;
ALTER TABLE payments ADD CONSTRAINT chk_payment_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');

-- A locally-created PaymentIntent is pending and has no paid timestamp until a
-- signed webhook confirms success.
ALTER TABLE payments ALTER COLUMN paid_at DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN paid_at DROP DEFAULT;
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE payments DROP CONSTRAINT chk_payment_status;
ALTER TABLE payments ADD CONSTRAINT chk_payment_status CHECK (
    status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')
);
ALTER TABLE payments ADD CONSTRAINT chk_payment_paid_at CHECK (
    status NOT IN ('SUCCEEDED', 'REFUNDED') OR paid_at IS NOT NULL
);

ALTER TABLE refunds ADD COLUMN provider_refund_id VARCHAR(64);
ALTER TABLE refunds ADD CONSTRAINT uk_refunds_provider_refund
    UNIQUE (provider_refund_id);

CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL DEFAULT 'STRIPE',
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_webhook_event UNIQUE (provider, event_id)
);

CREATE INDEX idx_webhook_events_type_created
    ON webhook_events(event_type, created_at DESC);
