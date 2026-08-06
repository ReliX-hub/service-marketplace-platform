-- One engagement has one full-refund lifecycle. Fail rather than silently choosing
-- among duplicate historical rows before installing the invariant.
DO $$
BEGIN
    IF EXISTS (
        SELECT engagement_id
          FROM refunds
         GROUP BY engagement_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce one refund per engagement: duplicate refund rows exist';
    END IF;
END $$;

CREATE UNIQUE INDEX uk_refunds_engagement
    ON refunds(engagement_id);

ALTER TABLE refunds
    ADD COLUMN provider_status VARCHAR(40),
    ADD COLUMN failure_message VARCHAR(500);
