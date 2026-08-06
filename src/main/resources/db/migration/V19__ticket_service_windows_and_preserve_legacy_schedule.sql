-- Listing-level service windows replace the legacy availability-slot concept.

ALTER TABLE tickets
    ADD COLUMN service_window_start TIMESTAMP WITH TIME ZONE,
    ADD COLUMN service_window_end TIMESTAMP WITH TIME ZONE;

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_service_window CHECK (
        service_window_end IS NULL
        OR service_window_start IS NULL
        OR service_window_end > service_window_start
    );

CREATE INDEX idx_tickets_open_service_window
    ON tickets(service_window_start, service_window_end)
    WHERE status = 'OPEN';

CREATE INDEX idx_tickets_open_service_window_end
    ON tickets(service_window_end)
    WHERE status = 'OPEN' AND service_window_end IS NOT NULL;

CREATE INDEX idx_tickets_open_expiration
    ON tickets(expires_at)
    WHERE status = 'OPEN' AND expires_at IS NOT NULL;

-- Preserve a historical slot as one coherent interval. A partially populated
-- engagement schedule is left untouched rather than combining two sources.
UPDATE engagements engagement
SET scheduled_start = slot.start_time,
    scheduled_end = slot.end_time
FROM time_slots slot
WHERE engagement.time_slot_id = slot.id
  AND engagement.scheduled_start IS NULL
  AND engagement.scheduled_end IS NULL;

-- The accepted application's proposed schedule is now the single source of
-- truth. Historical slot timing has been preserved above, so the disconnected
-- booking-era relation can be removed without losing engagement schedules.
ALTER TABLE engagements DROP COLUMN time_slot_id;
DROP TABLE time_slots;
