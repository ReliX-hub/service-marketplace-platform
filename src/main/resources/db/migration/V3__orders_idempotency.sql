ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64);

ALTER TABLE orders ADD CONSTRAINT uk_orders_customer_idempotency
    UNIQUE(customer_id, idempotency_key);