-- Add IN_PROGRESS status
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;
ALTER TABLE orders ADD CONSTRAINT chk_order_status
    CHECK (status IN ('PENDING', 'PAID', 'CONFIRMED', 'IN_PROGRESS', 'CANCELLED', 'COMPLETED'));

-- Add provider operation timestamp fields
ALTER TABLE orders ADD COLUMN accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500);

-- Add index to optimize provider order queries
CREATE INDEX idx_orders_provider_status ON orders(provider_id, status);
