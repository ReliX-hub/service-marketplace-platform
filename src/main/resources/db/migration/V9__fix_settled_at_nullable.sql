-- V9: Make settled_at nullable (set only when batch processing completes, not at creation)
ALTER TABLE settlements ALTER COLUMN settled_at DROP NOT NULL;
ALTER TABLE settlements ALTER COLUMN settled_at DROP DEFAULT;
