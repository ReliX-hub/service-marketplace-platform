-- Existing external document_url values are retained as legacy references.
-- New uploads use one normalized LARGE stored file and never write a new
-- external URL.

ALTER TABLE worker_credentials
    ADD COLUMN document_file_id BIGINT REFERENCES stored_files(id);

CREATE UNIQUE INDEX uk_worker_credentials_document
    ON worker_credentials(document_file_id)
    WHERE document_file_id IS NOT NULL;

COMMENT ON COLUMN worker_credentials.document_url IS
    'Legacy external URL retained for migrated/seeded records only.';
COMMENT ON COLUMN worker_credentials.document_file_id IS
    'PRIVATE normalized LARGE image; readable only by the owning worker and ADMIN.';
