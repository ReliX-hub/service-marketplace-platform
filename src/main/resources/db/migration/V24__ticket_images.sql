ALTER TABLE tickets
    ADD COLUMN image_count SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN cover_image_large_url VARCHAR(500);

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_image_count
        CHECK (image_count >= 0 AND image_count <= 8);

CREATE TABLE ticket_images (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    thumb_id BIGINT NOT NULL REFERENCES stored_files(id),
    large_id BIGINT NOT NULL REFERENCES stored_files(id),
    caption VARCHAR(160),
    position SMALLINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- DEFERRABLE is intentional: a reorder defers this constraint and writes
    -- every final position directly. This preserves the non-negative CHECK and
    -- avoids the invalid negative-position intermediate state.
    CONSTRAINT uk_ticket_images_position
        UNIQUE (ticket_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT uk_ticket_images_thumb UNIQUE (thumb_id),
    CONSTRAINT uk_ticket_images_large UNIQUE (large_id),
    CONSTRAINT chk_ti_position CHECK (position >= 0 AND position < 8),
    CONSTRAINT chk_ti_distinct CHECK (thumb_id <> large_id)
);

CREATE INDEX idx_ticket_images_ticket
    ON ticket_images(ticket_id, position);

CREATE TRIGGER update_ticket_images_updated_at
    BEFORE UPDATE ON ticket_images
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON COLUMN tickets.cover_image_url IS
    'Denormalized relative URL of the first ticket image thumb. Maintained only by TicketImageService.';
COMMENT ON COLUMN tickets.cover_image_large_url IS
    'Denormalized relative URL of the first ticket image large variant. Maintained only by TicketImageService.';
COMMENT ON COLUMN tickets.image_count IS
    'Denormalized image count used by ticket-board list responses.';
