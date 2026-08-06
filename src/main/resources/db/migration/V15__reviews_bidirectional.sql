-- Convert the old one-way customer-to-provider review into one review per
-- engagement and direction, with both participants represented by user IDs.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM reviews review
        LEFT JOIN worker_profiles worker ON worker.id = review.provider_id
        WHERE worker.id IS NULL
    ) THEN
        RAISE EXCEPTION 'At least one legacy review references an unknown worker profile';
    END IF;
END
$$;

ALTER TABLE reviews DROP CONSTRAINT reviews_order_id_key;
ALTER TABLE reviews DROP CONSTRAINT reviews_provider_id_fkey;

ALTER TABLE reviews RENAME COLUMN order_id TO engagement_id;
ALTER TABLE reviews RENAME COLUMN customer_id TO reviewer_id;
ALTER TABLE reviews RENAME COLUMN provider_id TO reviewee_id;

ALTER TABLE reviews RENAME CONSTRAINT reviews_order_id_fkey TO reviews_engagement_id_fkey;
ALTER TABLE reviews RENAME CONSTRAINT reviews_customer_id_fkey TO reviews_reviewer_id_fkey;

-- reviewee_id still contains the legacy worker_profile ID at this point.
UPDATE reviews review
SET reviewee_id = worker.user_id
FROM worker_profiles worker
WHERE review.reviewee_id = worker.id;

ALTER TABLE reviews ADD CONSTRAINT reviews_reviewee_id_fkey
    FOREIGN KEY (reviewee_id) REFERENCES users(id);

ALTER TABLE reviews ADD COLUMN direction VARCHAR(20);
UPDATE reviews SET direction = 'CLIENT_TO_WORKER';
ALTER TABLE reviews ALTER COLUMN direction SET NOT NULL;
ALTER TABLE reviews ADD CONSTRAINT chk_review_direction CHECK (
    direction IN ('CLIENT_TO_WORKER', 'WORKER_TO_CLIENT')
);
ALTER TABLE reviews ADD CONSTRAINT uk_reviews_engagement_direction
    UNIQUE (engagement_id, direction);

ALTER INDEX idx_reviews_customer_id RENAME TO idx_reviews_reviewer_id;
ALTER INDEX idx_reviews_provider_id RENAME TO idx_reviews_reviewee_id;
