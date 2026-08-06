-- Matching applications and the marketplace engagement aggregate.

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    proposed_amount DECIMAL(12, 2) NOT NULL,
    message TEXT,
    proposed_start TIMESTAMP WITH TIME ZONE,
    proposed_end TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_applications_ticket
        FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_applicant
        FOREIGN KEY (applicant_id) REFERENCES users(id),
    CONSTRAINT uk_applications_ticket_applicant UNIQUE (ticket_id, applicant_id),
    CONSTRAINT chk_application_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT chk_application_amount CHECK (proposed_amount >= 0),
    CONSTRAINT chk_application_schedule CHECK (
        proposed_end IS NULL OR proposed_start IS NULL OR proposed_end > proposed_start
    )
);

CREATE UNIQUE INDEX uk_applications_one_accepted_per_ticket
    ON applications(ticket_id)
    WHERE status = 'ACCEPTED';
CREATE INDEX idx_applications_ticket_status
    ON applications(ticket_id, status, created_at);
CREATE INDEX idx_applications_applicant_status
    ON applications(applicant_id, status, created_at DESC);

CREATE TRIGGER update_applications_updated_at
    BEFORE UPDATE ON applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE orders RENAME TO engagements;
ALTER TABLE engagements RENAME COLUMN customer_id TO client_id;
ALTER TABLE engagements RENAME COLUMN provider_id TO worker_id;
ALTER TABLE engagements RENAME COLUMN total_price TO amount;

ALTER TABLE engagements RENAME CONSTRAINT orders_pkey TO engagements_pkey;
ALTER TABLE engagements RENAME CONSTRAINT orders_customer_id_fkey TO engagements_client_id_fkey;
ALTER TABLE engagements RENAME CONSTRAINT orders_provider_id_fkey TO engagements_worker_id_fkey;
ALTER TABLE engagements RENAME CONSTRAINT orders_time_slot_id_fkey TO engagements_time_slot_id_fkey;
ALTER TABLE engagements RENAME CONSTRAINT uk_orders_customer_idempotency TO uk_engagements_client_idempotency;

ALTER SEQUENCE orders_id_seq RENAME TO engagements_id_seq;
ALTER TRIGGER update_orders_updated_at ON engagements
    RENAME TO update_engagements_updated_at;

ALTER INDEX idx_orders_customer_id RENAME TO idx_engagements_client_id;
ALTER INDEX idx_orders_provider_id RENAME TO idx_engagements_worker_id;
ALTER INDEX idx_orders_status RENAME TO idx_engagements_status;
ALTER INDEX idx_orders_created_at RENAME TO idx_engagements_created_at;
ALTER INDEX idx_orders_provider_status RENAME TO idx_engagements_worker_status;

ALTER TABLE engagements ADD COLUMN ticket_id BIGINT;
ALTER TABLE engagements ADD COLUMN application_id BIGINT;
ALTER TABLE engagements ADD COLUMN scheduled_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN scheduled_end TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN funded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN disputed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE engagements ADD COLUMN dispute_reason VARCHAR(500);

UPDATE engagements engagement
SET ticket_id = ticket.id
FROM tickets ticket
WHERE ticket.legacy_service_id = engagement.service_id;

UPDATE engagements engagement
SET scheduled_start = slot.start_time,
    scheduled_end = slot.end_time
FROM time_slots slot
WHERE slot.id = engagement.time_slot_id;

-- Historical PAID-or-later orders should have a successful payment. Older demo
-- data did not always satisfy that invariant, so reconcile it before switching
-- to the escrow state machine.
INSERT INTO payments (
    order_id, request_id, amount, status, paid_at, created_at, updated_at
)
SELECT
    engagement.id,
    'legacy-migration-' || engagement.id,
    engagement.amount,
    'SUCCEEDED',
    COALESCE(engagement.started_at, engagement.completed_at, engagement.updated_at),
    engagement.created_at,
    engagement.updated_at
FROM engagements engagement
WHERE engagement.status IN ('PAID', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED')
  AND NOT EXISTS (
      SELECT 1 FROM payments payment WHERE payment.order_id = engagement.id
  )
ON CONFLICT ON CONSTRAINT uk_payments_order DO NOTHING;

UPDATE engagements engagement
SET funded_at = payment.paid_at
FROM payments payment
WHERE payment.order_id = engagement.id
  AND engagement.status IN ('PAID', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED');

UPDATE engagements
SET delivered_at = COALESCE(delivered_at, completed_at, updated_at),
    approved_at = COALESCE(approved_at, completed_at, updated_at)
WHERE status = 'COMPLETED';

UPDATE settlements
SET settled_at = COALESCE(settled_at, processed_at, created_at),
    processed_at = COALESCE(processed_at, settled_at, created_at)
WHERE status = 'COMPLETED';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM engagements WHERE ticket_id IS NULL) THEN
        RAISE EXCEPTION 'At least one legacy order could not be mapped to a ticket';
    END IF;
END
$$;

ALTER TABLE engagements ALTER COLUMN ticket_id SET NOT NULL;
ALTER TABLE engagements ADD CONSTRAINT engagements_ticket_id_fkey
    FOREIGN KEY (ticket_id) REFERENCES tickets(id);
-- application_id intentionally remains nullable for historical engagements:
-- an old reusable service could have many orders, while a new ticket permits
-- only one accepted application.
ALTER TABLE engagements ADD CONSTRAINT engagements_application_id_fkey
    FOREIGN KEY (application_id) REFERENCES applications(id);
CREATE UNIQUE INDEX uk_engagements_application
    ON engagements(application_id)
    WHERE application_id IS NOT NULL;
CREATE INDEX idx_engagements_ticket_status
    ON engagements(ticket_id, status, created_at DESC);
ALTER TABLE engagements ADD CONSTRAINT chk_engagement_amount CHECK (amount >= 0);
ALTER TABLE engagements ADD CONSTRAINT chk_engagement_schedule CHECK (
    scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end > scheduled_start
);

-- Preserve the meaning of historical audit entries while IDs are unchanged.
UPDATE audit_logs audit
SET entity_type = 'TICKET',
    entity_id = ticket.id
FROM tickets ticket
WHERE audit.entity_type = 'SERVICE'
  AND audit.entity_id = ticket.legacy_service_id;

UPDATE audit_logs
SET entity_type = 'ENGAGEMENT',
    action = REGEXP_REPLACE(action, '^ORDER_', 'ENGAGEMENT_')
WHERE entity_type = 'ORDER';

UPDATE audit_logs SET actor_type = 'CLIENT' WHERE actor_type = 'CUSTOMER';
UPDATE audit_logs audit
SET actor_id = worker.user_id,
    actor_type = 'WORKER'
FROM worker_profiles worker
WHERE audit.actor_type = 'PROVIDER'
  AND audit.actor_id = worker.id;
UPDATE audit_logs SET actor_type = 'WORKER' WHERE actor_type = 'PROVIDER';

-- The ticket mapping is complete, so the legacy service FK and table can go.
ALTER TABLE engagements DROP COLUMN service_id;
DROP TABLE services;
ALTER TABLE tickets DROP COLUMN legacy_service_id;

ALTER TABLE payments RENAME COLUMN order_id TO engagement_id;
ALTER TABLE payments RENAME CONSTRAINT payments_order_id_fkey TO payments_engagement_id_fkey;
ALTER TABLE payments RENAME CONSTRAINT uk_payments_order TO uk_payments_engagement;

ALTER TABLE refunds RENAME COLUMN order_id TO engagement_id;
ALTER TABLE refunds RENAME CONSTRAINT refunds_order_id_fkey TO refunds_engagement_id_fkey;
ALTER INDEX idx_refunds_order RENAME TO idx_refunds_engagement;

ALTER TABLE settlements RENAME COLUMN order_id TO engagement_id;
ALTER TABLE settlements RENAME CONSTRAINT settlements_order_id_fkey TO settlements_engagement_id_fkey;
ALTER TABLE settlements RENAME CONSTRAINT settlements_order_id_key TO uk_settlements_engagement;
ALTER INDEX idx_settlements_order_id RENAME TO idx_settlements_engagement_id;

ALTER TABLE time_slots RENAME COLUMN provider_id TO worker_id;
ALTER TABLE time_slots RENAME CONSTRAINT time_slots_provider_id_fkey TO time_slots_worker_id_fkey;
ALTER INDEX idx_time_slots_provider_id RENAME TO idx_time_slots_worker_id;
ALTER INDEX idx_time_slots_availability RENAME TO idx_time_slots_worker_availability;
