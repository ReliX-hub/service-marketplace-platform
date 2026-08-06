-- Replace the booking lifecycle with the escrow-style engagement lifecycle.

ALTER TABLE engagements DROP CONSTRAINT chk_order_status;

-- A completed refund is more specific than the legacy terminal CANCELLED state.
UPDATE engagements engagement
SET status = 'REFUNDED'
WHERE engagement.status = 'CANCELLED'
  AND (
      EXISTS (
          SELECT 1
          FROM refunds refund
          WHERE refund.engagement_id = engagement.id
            AND refund.status = 'COMPLETED'
      )
      OR EXISTS (
          SELECT 1
          FROM payments payment
          WHERE payment.engagement_id = engagement.id
            AND payment.status = 'REFUNDED'
      )
  );

UPDATE engagements
SET status = CASE status
    WHEN 'PENDING' THEN 'ACCEPTED'
    WHEN 'PAID' THEN 'FUNDED'
    WHEN 'CONFIRMED' THEN 'FUNDED'
    ELSE status
END;

ALTER TABLE engagements ALTER COLUMN status SET DEFAULT 'ACCEPTED';
ALTER TABLE engagements ADD CONSTRAINT chk_engagement_status CHECK (
    status IN (
        'ACCEPTED', 'FUNDED', 'IN_PROGRESS', 'DELIVERED',
        'COMPLETED', 'DISPUTED', 'CANCELLED', 'REFUNDED'
    )
);
ALTER TABLE engagements ADD CONSTRAINT chk_engagement_dispute_details CHECK (
    status <> 'DISPUTED'
    OR (
        disputed_at IS NOT NULL
        AND NULLIF(BTRIM(dispute_reason), '') IS NOT NULL
    )
);
