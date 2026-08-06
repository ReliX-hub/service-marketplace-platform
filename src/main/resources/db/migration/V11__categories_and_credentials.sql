-- Marketplace category catalog and worker qualification records.

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(100),
    required_credential VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT chk_category_not_own_parent
        CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT chk_category_required_credential
        CHECK (required_credential IS NULL OR required_credential IN (
            'ELECTRICAL_LICENSE', 'DRIVER_LICENSE', 'BACKGROUND_CHECK'
        ))
);

CREATE INDEX idx_categories_parent ON categories(parent_id);
CREATE INDEX idx_categories_active ON categories(active, name);

CREATE TRIGGER update_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO categories (code, name, description, icon, required_credential) VALUES
    ('HOME_CLEANING', 'Home Cleaning', 'Routine, deep, and move-out cleaning services.', 'sparkles', NULL),
    ('PLUMBING', 'Plumbing', 'Household plumbing installation and repair.', 'wrench', NULL),
    ('ELECTRICAL', 'Electrical', 'Licensed residential and small-business electrical work.', 'zap', 'ELECTRICAL_LICENSE'),
    ('MOVING', 'Moving', 'Packing, loading, unloading, and local moving help.', 'truck', NULL),
    ('DELIVERY', 'Delivery', 'Local pickup and delivery by a licensed driver.', 'package', 'DRIVER_LICENSE'),
    ('SPORTS_COACHING', 'Sports Coaching', 'Individual and small-group sports instruction.', 'trophy', NULL),
    ('TUTORING', 'Tutoring', 'Academic tutoring and test preparation.', 'book-open', NULL),
    ('PET_CARE', 'Pet Care', 'Pet sitting, walking, feeding, and check-ins.', 'paw-print', NULL),
    ('CHILDCARE', 'Childcare', 'Short-term and recurring childcare support.', 'baby', 'BACKGROUND_CHECK'),
    ('TECH_SUPPORT', 'Tech Support', 'Remote and on-site help with computers and networks.', 'monitor', NULL),
    ('EVENT_HELP', 'Event Help', 'Setup, teardown, registration, and event staffing.', 'calendar', NULL),
    ('HANDYMAN', 'Handyman', 'Assembly, installation, and general household repairs.', 'hammer', NULL),
    ('PERSONAL_CARE', 'Personal Care', 'Hair, beauty, wellness, and personal care services.', 'heart', NULL);

CREATE TABLE worker_credentials (
    id BIGSERIAL PRIMARY KEY,
    worker_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    credential_number VARCHAR(100),
    document_url VARCHAR(500),
    issued_at DATE,
    expires_at DATE,
    rejection_reason VARCHAR(500),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_worker_credentials_worker
        FOREIGN KEY (worker_id) REFERENCES worker_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_worker_credentials_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_worker_credentials_worker_type UNIQUE (worker_id, type),
    CONSTRAINT chk_worker_credential_type CHECK (type IN (
        'ELECTRICAL_LICENSE', 'DRIVER_LICENSE', 'BACKGROUND_CHECK'
    )),
    CONSTRAINT chk_worker_credential_status CHECK (status IN (
        'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'
    )),
    CONSTRAINT chk_worker_credential_dates CHECK (
        expires_at IS NULL OR issued_at IS NULL OR expires_at >= issued_at
    ),
    CONSTRAINT chk_worker_credential_rejection CHECK (
        status <> 'REJECTED' OR NULLIF(BTRIM(rejection_reason), '') IS NOT NULL
    )
);

CREATE INDEX idx_worker_credentials_status
    ON worker_credentials(status, created_at);
CREATE INDEX idx_worker_credentials_worker_status
    ON worker_credentials(worker_id, status);

CREATE TRIGGER update_worker_credentials_updated_at
    BEFORE UPDATE ON worker_credentials
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
