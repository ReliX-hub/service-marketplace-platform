CREATE TABLE engagement_deliverables (
    id BIGSERIAL PRIMARY KEY,
    engagement_id BIGINT NOT NULL REFERENCES engagements(id) ON DELETE CASCADE,
    thumb_id BIGINT NOT NULL REFERENCES stored_files(id),
    large_id BIGINT NOT NULL REFERENCES stored_files(id),
    note VARCHAR(300),
    position SMALLINT NOT NULL,
    submitted_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_engagement_deliverables_thumb UNIQUE (thumb_id),
    CONSTRAINT uk_engagement_deliverables_large UNIQUE (large_id),
    CONSTRAINT uk_engagement_deliverables_position
        UNIQUE (engagement_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT chk_engagement_deliverables_position CHECK (position >= 0 AND position < 8),
    CONSTRAINT chk_engagement_deliverables_distinct CHECK (thumb_id <> large_id)
);

CREATE INDEX idx_engagement_deliverables_engagement
    ON engagement_deliverables(engagement_id, position);

ALTER TABLE engagements
    ADD COLUMN deliverable_count SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE engagements
    ADD CONSTRAINT chk_engagement_deliverable_count
        CHECK (deliverable_count >= 0 AND deliverable_count <= 8);
