-- A ticket is a marketplace listing: either a worker OFFER or a client REQUEST.

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    author_id BIGINT NOT NULL,
    worker_id BIGINT,
    category_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    pricing_mode VARCHAR(20) NOT NULL,
    price DECIMAL(12, 2),
    budget_min DECIMAL(12, 2),
    budget_max DECIMAL(12, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    location_mode VARCHAR(20) NOT NULL,
    address VARCHAR(500),
    city VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    estimated_duration_minutes INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    cover_image_url VARCHAR(500),
    view_count BIGINT NOT NULL DEFAULT 0,
    application_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    legacy_service_id BIGINT,
    CONSTRAINT fk_tickets_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_tickets_worker FOREIGN KEY (worker_id) REFERENCES worker_profiles(id),
    CONSTRAINT fk_tickets_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT uk_tickets_legacy_service UNIQUE (legacy_service_id),
    CONSTRAINT chk_ticket_direction CHECK (
        (kind = 'OFFER' AND worker_id IS NOT NULL)
        OR (kind = 'REQUEST' AND worker_id IS NULL)
    ),
    CONSTRAINT chk_ticket_pricing CHECK (
        (pricing_mode = 'FIXED'
            AND price IS NOT NULL AND price >= 0
            AND budget_min IS NULL AND budget_max IS NULL)
        OR (pricing_mode = 'BUDGET_RANGE'
            AND price IS NULL
            AND budget_min IS NOT NULL AND budget_min >= 0
            AND budget_max IS NOT NULL AND budget_max >= budget_min)
        OR (pricing_mode = 'OPEN_BID'
            AND price IS NULL AND budget_min IS NULL AND budget_max IS NULL)
    ),
    CONSTRAINT chk_ticket_status CHECK (
        status IN ('DRAFT', 'OPEN', 'MATCHED', 'CLOSED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT chk_ticket_location_mode CHECK (
        location_mode IN ('ON_SITE', 'REMOTE', 'HYBRID')
    ),
    CONSTRAINT chk_ticket_title CHECK (NULLIF(BTRIM(title), '') IS NOT NULL),
    CONSTRAINT chk_ticket_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ticket_duration CHECK (
        estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0
    ),
    CONSTRAINT chk_ticket_counts CHECK (view_count >= 0 AND application_count >= 0),
    CONSTRAINT chk_ticket_latitude CHECK (
        latitude IS NULL OR (latitude >= -90 AND latitude <= 90)
    ),
    CONSTRAINT chk_ticket_longitude CHECK (
        longitude IS NULL OR (longitude >= -180 AND longitude <= 180)
    )
);

CREATE INDEX idx_tickets_open_discovery
    ON tickets(kind, category_id, created_at DESC)
    WHERE status = 'OPEN';
CREATE INDEX idx_tickets_author_status
    ON tickets(author_id, status, created_at DESC);
CREATE INDEX idx_tickets_worker_status
    ON tickets(worker_id, status)
    WHERE worker_id IS NOT NULL;
CREATE INDEX idx_tickets_open_location
    ON tickets(city, location_mode)
    WHERE status = 'OPEN';
CREATE INDEX idx_tickets_search
    ON tickets USING GIN (
        to_tsvector('english'::regconfig, COALESCE(title, '') || ' ' || COALESCE(description, ''))
    );

CREATE TRIGGER update_tickets_updated_at
    BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Existing services have no category. PERSONAL_CARE accurately represents the
-- historical haircut, massage, and facial demo data and is also a useful
-- marketplace category for real upgraded databases.
INSERT INTO tickets (
    kind, author_id, worker_id, category_id, title, description,
    pricing_mode, price, currency, location_mode, address,
    estimated_duration_minutes, status, created_at, updated_at,
    legacy_service_id
)
SELECT
    'OFFER',
    wp.user_id,
    wp.id,
    category.id,
    service.name,
    service.description,
    'FIXED',
    service.price,
    'USD',
    'ON_SITE',
    wp.address,
    service.duration_minutes,
    CASE service.status WHEN 'ACTIVE' THEN 'OPEN' ELSE 'CLOSED' END,
    service.created_at,
    service.updated_at,
    service.id
FROM services service
JOIN worker_profiles wp ON wp.id = service.provider_id
JOIN categories category ON category.code = 'PERSONAL_CARE';

DO $$
DECLARE
    service_count BIGINT;
    migrated_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO service_count FROM services;
    SELECT COUNT(*) INTO migrated_count FROM tickets WHERE legacy_service_id IS NOT NULL;

    IF service_count <> migrated_count THEN
        RAISE EXCEPTION 'Service-to-ticket migration mismatch: services=%, tickets=%',
            service_count, migrated_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM tickets
        WHERE legacy_service_id IS NOT NULL
          AND (author_id IS NULL OR worker_id IS NULL OR category_id IS NULL)
    ) THEN
        RAISE EXCEPTION 'A migrated service ticket is missing required ownership or category data';
    END IF;
END
$$;
