-- Central registry for immutable, normalized image variants. owner_id is a
-- polymorphic aggregate id whose meaning is fixed by owner_type:
-- TICKET_IMAGE -> tickets.id, ENGAGEMENT_DELIVERABLE -> engagements.id,
-- CREDENTIAL_DOCUMENT -> worker_credentials.id, USER_AVATAR -> users.id.

CREATE TABLE stored_files (
    id BIGSERIAL PRIMARY KEY,
    storage_key VARCHAR(180) NOT NULL,
    variant VARCHAR(20) NOT NULL,
    visibility VARCHAR(10) NOT NULL,
    owner_type VARCHAR(30) NOT NULL,
    owner_id BIGINT,
    uploader_id BIGINT NOT NULL REFERENCES users(id),
    content_type VARCHAR(60) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_stored_files_key UNIQUE (storage_key),
    CONSTRAINT chk_sf_variant CHECK (variant IN ('THUMB', 'LARGE')),
    CONSTRAINT chk_sf_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_sf_owner_type CHECK (owner_type IN (
        'TICKET_IMAGE',
        'ENGAGEMENT_DELIVERABLE',
        'CREDENTIAL_DOCUMENT',
        'USER_AVATAR'
    )),
    -- WebP is accepted as input, then normalized to JPEG or PNG. The registry
    -- records the generated variant's actual representation.
    CONSTRAINT chk_sf_content_type CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_sf_owner_id CHECK (owner_id IS NULL OR owner_id > 0),
    CONSTRAINT chk_sf_size CHECK (byte_size > 0),
    CONSTRAINT chk_sf_dims CHECK (width > 0 AND height > 0)
);

CREATE INDEX idx_stored_files_owner
    ON stored_files(owner_type, owner_id);
CREATE INDEX idx_stored_files_orphan
    ON stored_files(created_at)
    WHERE owner_id IS NULL;

CREATE TRIGGER update_stored_files_updated_at
    BEFORE UPDATE ON stored_files
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON COLUMN stored_files.owner_id IS
    'Aggregate id interpreted according to owner_type; NULL means staged or detached and inaccessible.';
