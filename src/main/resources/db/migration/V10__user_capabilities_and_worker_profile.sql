-- Separate the administrative role from marketplace capabilities and evolve
-- provider records into worker profiles without rewriting migration history.

CREATE TABLE user_capabilities (
    user_id BIGINT NOT NULL,
    capability VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_capabilities PRIMARY KEY (user_id, capability),
    CONSTRAINT fk_user_capabilities_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_capability
        CHECK (capability IN ('CLIENT', 'WORKER'))
);

CREATE INDEX idx_user_capabilities_capability
    ON user_capabilities(capability, user_id);

INSERT INTO user_capabilities (user_id, capability)
SELECT u.id, capabilities.capability
FROM users u
CROSS JOIN (VALUES ('CLIENT'), ('WORKER')) AS capabilities(capability);

ALTER TABLE users DROP CONSTRAINT chk_user_role;
UPDATE users
SET role = 'USER'
WHERE role IN ('CUSTOMER', 'PROVIDER');
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT chk_user_role
    CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE users ADD COLUMN client_rating DECIMAL(3, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE users ADD COLUMN client_review_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);
ALTER TABLE users ADD CONSTRAINT chk_user_client_rating
    CHECK (client_rating >= 0.00 AND client_rating <= 5.00);
ALTER TABLE users ADD CONSTRAINT chk_user_client_review_count
    CHECK (client_review_count >= 0);

ALTER TABLE providers RENAME TO worker_profiles;
ALTER TABLE worker_profiles RENAME COLUMN business_name TO display_name;
ALTER TABLE worker_profiles ADD COLUMN headline VARCHAR(255);
ALTER TABLE worker_profiles ADD COLUMN completed_jobs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE worker_profiles ADD COLUMN service_radius_km DECIMAL(8, 2);

UPDATE worker_profiles SET rating = 0.00 WHERE rating IS NULL;
UPDATE worker_profiles SET review_count = 0 WHERE review_count IS NULL;
UPDATE worker_profiles SET verified = FALSE WHERE verified IS NULL;

ALTER TABLE worker_profiles ALTER COLUMN rating SET NOT NULL;
ALTER TABLE worker_profiles ALTER COLUMN review_count SET NOT NULL;
ALTER TABLE worker_profiles ALTER COLUMN verified SET NOT NULL;

UPDATE worker_profiles wp
SET completed_jobs = completed.count
FROM (
    SELECT provider_id, COUNT(*)::INTEGER AS count
    FROM orders
    WHERE status = 'COMPLETED'
    GROUP BY provider_id
) completed
WHERE completed.provider_id = wp.id;

ALTER TABLE worker_profiles ADD CONSTRAINT chk_worker_profile_review_count
    CHECK (review_count >= 0);
ALTER TABLE worker_profiles ADD CONSTRAINT chk_worker_profile_completed_jobs
    CHECK (completed_jobs >= 0);
ALTER TABLE worker_profiles ADD CONSTRAINT chk_worker_profile_service_radius
    CHECK (service_radius_km IS NULL OR service_radius_km >= 0);

-- PostgreSQL updates dependency definitions during a table rename, but keeps
-- the old object names. Rename them explicitly so the resulting schema uses
-- marketplace terminology throughout.
ALTER TABLE worker_profiles RENAME CONSTRAINT providers_pkey TO worker_profiles_pkey;
ALTER TABLE worker_profiles RENAME CONSTRAINT providers_user_id_fkey TO worker_profiles_user_id_fkey;
ALTER TABLE worker_profiles RENAME CONSTRAINT providers_user_id_key TO uk_worker_profiles_user;
ALTER TABLE worker_profiles RENAME CONSTRAINT chk_rating_range TO chk_worker_profile_rating;

ALTER SEQUENCE providers_id_seq RENAME TO worker_profiles_id_seq;
ALTER TRIGGER update_providers_updated_at ON worker_profiles
    RENAME TO update_worker_profiles_updated_at;

ALTER INDEX idx_providers_user_id RENAME TO idx_worker_profiles_user_id;
ALTER INDEX idx_providers_verified RENAME TO idx_worker_profiles_verified;
ALTER INDEX idx_providers_rating RENAME TO idx_worker_profiles_rating;
