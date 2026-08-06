-- Pending proposals are terminal when their ticket reaches its application or
-- service-window deadline.

ALTER TABLE applications DROP CONSTRAINT chk_application_status;

ALTER TABLE applications ADD CONSTRAINT chk_application_status CHECK (
    status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')
);

UPDATE applications application
SET status = 'EXPIRED',
    updated_at = CURRENT_TIMESTAMP
FROM tickets ticket
WHERE application.ticket_id = ticket.id
  AND application.status = 'PENDING'
  AND (
      ticket.status = 'EXPIRED'
      OR (
          ticket.status = 'OPEN'
          AND (
              (ticket.expires_at IS NOT NULL AND ticket.expires_at <= CURRENT_TIMESTAMP)
              OR (ticket.service_window_end IS NOT NULL
                  AND ticket.service_window_end <= CURRENT_TIMESTAMP)
          )
      )
  );

UPDATE tickets
SET status = 'EXPIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'OPEN'
  AND (
      (expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP)
      OR (service_window_end IS NOT NULL AND service_window_end <= CURRENT_TIMESTAMP)
  );
