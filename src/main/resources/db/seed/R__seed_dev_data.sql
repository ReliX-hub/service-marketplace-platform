-- Development-only marketplace data. Every relationship is resolved through a
-- stable natural key (email, category code, or ticket title), never a generated ID.

TRUNCATE TABLE
    webhook_events,
    reviews,
    refunds,
    settlements,
    payments,
    engagement_deliverables,
    engagements,
    applications,
    ticket_images,
    tickets,
    worker_credentials,
    stored_files,
    user_capabilities,
    refresh_tokens,
    audit_logs,
    settlement_batches,
    worker_profiles,
    users
RESTART IDENTITY CASCADE;

-- Password for every demo account: Demo1234!
INSERT INTO users (
    email, password_hash, name, phone, role, status, avatar_url
) VALUES
    ('admin@marketplace.com', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'System Admin', '3125550100', 'ADMIN', 'ACTIVE', 'https://images.example.com/avatars/admin.png'),
    ('john@example.com', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'John Carter', '3125550101', 'USER', 'ACTIVE', 'https://images.example.com/avatars/john.png'),
    ('jane@example.com', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Jane Park', '3125550102', 'USER', 'ACTIVE', 'https://images.example.com/avatars/jane.png'),
    ('bob@hairsalon.com', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Bob Martinez', '3125550103', 'USER', 'ACTIVE', 'https://images.example.com/avatars/bob.png'),
    ('alice@spa.com', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Alice Chen', '3125550104', 'USER', 'ACTIVE', 'https://images.example.com/avatars/alice.png'),
    ('liam@brightwire.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Liam Brooks', '3125550105', 'USER', 'ACTIVE', 'https://images.example.com/avatars/liam.png'),
    ('sofia@freshstart.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Sofia Reyes', '3125550106', 'USER', 'ACTIVE', 'https://images.example.com/avatars/sofia.png'),
    ('noah@swiftmove.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Noah Williams', '3125550107', 'USER', 'ACTIVE', 'https://images.example.com/avatars/noah.png'),
    ('emma@carecircle.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Emma Davis', '3125550108', 'USER', 'ACTIVE', 'https://images.example.com/avatars/emma.png'),
    ('oliver@techhand.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Oliver Grant', '3125550109', 'USER', 'ACTIVE', 'https://images.example.com/avatars/oliver.png'),
    ('ava@learnplay.example', '$2a$10$Gz8gULpYebMXfY31aS6QH.AFUlHZ6YXCBxrjbG7QDsYqSQ7fo0W7e', 'Ava Thompson', '3125550110', 'USER', 'ACTIVE', 'https://images.example.com/avatars/ava.png');

INSERT INTO user_capabilities (user_id, capability)
SELECT user_account.id, capability.value
FROM users user_account
CROSS JOIN (VALUES ('CLIENT'), ('WORKER')) AS capability(value);

WITH profile_seed (
    email, display_name, description, headline, address,
    latitude, longitude, verified, service_radius_km
) AS (
    VALUES
        ('bob@hairsalon.com', 'Bob''s Mobile Grooming', 'Mobile haircuts, grooming, and event-ready styling.', 'Personal care at your door', '412 W Oak St, Chicago, IL', 41.88720000, -87.63730000, TRUE, 22.00),
        ('alice@spa.com', 'Alice Wellness & Cleaning', 'Wellness sessions and detail-oriented home refresh services.', 'Calm spaces and restorative care', '825 N Clark St, Chicago, IL', 41.89780000, -87.63150000, TRUE, 18.00),
        ('liam@brightwire.example', 'BrightWire Electrical', 'Licensed residential electrical diagnostics and repair.', 'Safe, code-conscious electrical work', '1940 W Irving Park Rd, Chicago, IL', 41.95430000, -87.67790000, TRUE, 35.00),
        ('sofia@freshstart.example', 'Fresh Start Services', 'Home cleaning and dependable event setup support.', 'A reliable extra pair of hands', '1635 S State St, Chicago, IL', 41.85930000, -87.62720000, TRUE, 28.00),
        ('noah@swiftmove.example', 'SwiftMove Local', 'Local moving, loading, and same-day delivery.', 'Careful handling, on-time arrival', '2550 N Milwaukee Ave, Chicago, IL', 41.92790000, -87.70350000, TRUE, 55.00),
        ('emma@carecircle.example', 'CareCircle Chicago', 'Background-checked childcare and attentive pet care.', 'Trusted care for families and pets', '1442 W Belmont Ave, Chicago, IL', 41.93970000, -87.66550000, TRUE, 20.00),
        ('oliver@techhand.example', 'TechHand Solutions', 'Remote tech support, Wi-Fi setup, assembly, and light repairs.', 'Technology and household fixes, explained clearly', '3145 N Lincoln Ave, Chicago, IL', 41.93890000, -87.66830000, TRUE, 30.00),
        ('ava@learnplay.example', 'Learn & Play Coaching', 'Patient academic tutoring and beginner sports coaching.', 'Practical coaching for steady progress', '5500 S Woodlawn Ave, Chicago, IL', 41.79440000, -87.59650000, TRUE, 25.00)
)
INSERT INTO worker_profiles (
    user_id, display_name, description, headline, address,
    latitude, longitude, rating, review_count, verified,
    completed_jobs, service_radius_km
)
SELECT
    user_account.id, seed.display_name, seed.description, seed.headline,
    seed.address, seed.latitude, seed.longitude, 0.00, 0, seed.verified,
    0, seed.service_radius_km
FROM profile_seed seed
JOIN users user_account ON user_account.email = seed.email;

WITH credential_seed (
    worker_email, type, status, credential_number, document_url,
    issued_at, expires_at, rejection_reason, reviewed_at
) AS (
    VALUES
        ('liam@brightwire.example', 'ELECTRICAL_LICENSE', 'VERIFIED', 'IL-ELEC-20481', 'https://documents.example.com/credentials/liam-electrical.pdf', CURRENT_DATE - 700, CURRENT_DATE + 395, NULL, CURRENT_TIMESTAMP - INTERVAL '40 days'),
        ('noah@swiftmove.example', 'DRIVER_LICENSE', 'VERIFIED', 'IL-DL-NW-7702', 'https://documents.example.com/credentials/noah-driver.pdf', CURRENT_DATE - 900, CURRENT_DATE + 190, NULL, CURRENT_TIMESTAMP - INTERVAL '25 days'),
        ('emma@carecircle.example', 'BACKGROUND_CHECK', 'VERIFIED', 'BG-EMMA-2026', 'https://documents.example.com/credentials/emma-background.pdf', CURRENT_DATE - 120, CURRENT_DATE + 245, NULL, CURRENT_TIMESTAMP - INTERVAL '18 days'),
        ('bob@hairsalon.com', 'ELECTRICAL_LICENSE', 'PENDING', 'IL-ELEC-PENDING-91', 'https://documents.example.com/credentials/bob-electrical.pdf', CURRENT_DATE - 60, CURRENT_DATE + 670, NULL, NULL),
        ('ava@learnplay.example', 'BACKGROUND_CHECK', 'REJECTED', 'BG-AVA-REVIEW', 'https://documents.example.com/credentials/ava-background.pdf', CURRENT_DATE - 45, CURRENT_DATE + 320, 'Uploaded document was incomplete.', CURRENT_TIMESTAMP - INTERVAL '3 days'),
        ('alice@spa.com', 'DRIVER_LICENSE', 'EXPIRED', 'IL-DL-AC-4431', 'https://documents.example.com/credentials/alice-driver.pdf', CURRENT_DATE - 900, CURRENT_DATE - 30, NULL, CURRENT_TIMESTAMP - INTERVAL '400 days')
)
INSERT INTO worker_credentials (
    worker_id, type, status, credential_number, document_url,
    issued_at, expires_at, rejection_reason, reviewed_by, reviewed_at
)
SELECT
    worker.id, seed.type, seed.status, seed.credential_number,
    seed.document_url, seed.issued_at, seed.expires_at,
    seed.rejection_reason, reviewer.id, seed.reviewed_at
FROM credential_seed seed
JOIN users worker_user ON worker_user.email = seed.worker_email
JOIN worker_profiles worker ON worker.user_id = worker_user.id
CROSS JOIN LATERAL (
    SELECT id FROM users WHERE email = 'admin@marketplace.com'
) reviewer;

WITH offer_seed (
    worker_email, category_code, title, description, pricing_mode,
    price, budget_min, budget_max, location_mode, city,
    duration_minutes, status, view_count, expiry_days
) AS (
    VALUES
        ('alice@spa.com', 'HOME_CLEANING', 'Weekly Home Refresh', 'Kitchen, bathrooms, floors, and a tidy reset for a two-bedroom home.', 'FIXED', 85.00, NULL, NULL, 'ON_SITE', 'Chicago', 180, 'OPEN', 84, 30),
        ('oliver@techhand.example', 'PLUMBING', 'Emergency Plumbing Visit', 'Assessment and minor repair for leaks, clogs, and fixture issues.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', 'Chicago', 120, 'OPEN', 57, 14),
        ('liam@brightwire.example', 'ELECTRICAL', 'Licensed Electrical Troubleshooting', 'Licensed diagnosis of outlets, switches, breakers, and lighting faults.', 'FIXED', 120.00, NULL, NULL, 'ON_SITE', 'Chicago', 120, 'MATCHED', 133, 30),
        ('noah@swiftmove.example', 'MOVING', 'Small Apartment Moving Help', 'Two-person loading and unloading support for a local apartment move.', 'BUDGET_RANGE', NULL, 180.00, 320.00, 'ON_SITE', 'Chicago', 240, 'OPEN', 96, 21),
        ('noah@swiftmove.example', 'DELIVERY', 'Same-Day Local Delivery', 'Door-to-door delivery for packages that fit in a passenger vehicle.', 'FIXED', 35.00, NULL, NULL, 'ON_SITE', 'Chicago', 60, 'MATCHED', 211, 14),
        ('ava@learnplay.example', 'SPORTS_COACHING', 'One-on-One Soccer Coaching', 'Fundamentals, footwork, and confidence-building drills for beginners.', 'BUDGET_RANGE', NULL, 45.00, 70.00, 'ON_SITE', 'Chicago', 75, 'OPEN', 61, 30),
        ('ava@learnplay.example', 'TUTORING', 'Patient Math Tutoring', 'Remote algebra and geometry tutoring with a written follow-up plan.', 'FIXED', 55.00, NULL, NULL, 'REMOTE', NULL, 60, 'MATCHED', 118, 30),
        ('emma@carecircle.example', 'PET_CARE', 'Neighborhood Pet Care', 'Dog walks, feeding, medication reminders, and photo updates.', 'FIXED', 28.00, NULL, NULL, 'ON_SITE', 'Chicago', 45, 'OPEN', 145, 21),
        ('emma@carecircle.example', 'CHILDCARE', 'Verified Childcare Support', 'Background-checked evening childcare with activity planning.', 'BUDGET_RANGE', NULL, 80.00, 140.00, 'ON_SITE', 'Chicago', 240, 'MATCHED', 176, 14),
        ('oliver@techhand.example', 'TECH_SUPPORT', 'Remote Computer Tune-Up', 'Malware scan, update review, backup check, and performance cleanup.', 'FIXED', 65.00, NULL, NULL, 'REMOTE', NULL, 90, 'MATCHED', 242, 30),
        ('sofia@freshstart.example', 'EVENT_HELP', 'Event Setup Assistant', 'Tables, signage, guest check-in, teardown, and venue reset.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', 'Chicago', 300, 'OPEN', 39, 21),
        ('oliver@techhand.example', 'HANDYMAN', 'Furniture Assembly and Repairs', 'Flat-pack assembly, shelf mounting, and light household repairs.', 'BUDGET_RANGE', NULL, 75.00, 160.00, 'ON_SITE', 'Chicago', 150, 'OPEN', 124, 21),
        ('bob@hairsalon.com', 'PERSONAL_CARE', 'Mobile Hair and Makeup', 'At-home event styling, hair, and camera-ready makeup.', 'FIXED', 110.00, NULL, NULL, 'ON_SITE', 'Chicago', 120, 'OPEN', 203, 30),
        ('alice@spa.com', 'HOME_CLEANING', 'Move-Out Deep Cleaning', 'Detailed move-out clean with appliance and cabinet interiors.', 'BUDGET_RANGE', NULL, 220.00, 340.00, 'ON_SITE', 'Chicago', 360, 'DRAFT', 0, 45),
        ('liam@brightwire.example', 'ELECTRICAL', 'Electrical Safety Inspection', 'Whole-home visual inspection and prioritized safety report.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', 'Chicago', 150, 'CLOSED', 72, 10),
        ('noah@swiftmove.example', 'MOVING', 'Two-Person Moving Crew', 'Reserved moving crew for a previously planned local move.', 'FIXED', 280.00, NULL, NULL, 'ON_SITE', 'Chicago', 300, 'CANCELLED', 51, 10),
        ('noah@swiftmove.example', 'DELIVERY', 'Evening Delivery Route', 'After-work delivery availability across the north side.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', 'Chicago', 120, 'EXPIRED', 88, -2),
        ('oliver@techhand.example', 'TECH_SUPPORT', 'Home Wi-Fi Optimization', 'Coverage review, router placement, and secure network setup.', 'BUDGET_RANGE', NULL, 90.00, 180.00, 'HYBRID', 'Chicago', 120, 'OPEN', 167, 30),
        ('emma@carecircle.example', 'PET_CARE', 'Dog Walking and Check-Ins', 'Flexible recurring dog walks and vacation check-ins.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', 'Chicago', 45, 'OPEN', 109, 30),
        ('alice@spa.com', 'PERSONAL_CARE', 'Wellness Massage Session', 'A calming in-home wellness massage session.', 'BUDGET_RANGE', NULL, 95.00, 140.00, 'ON_SITE', 'Chicago', 90, 'OPEN', 154, 30)
)
INSERT INTO tickets (
    kind, author_id, worker_id, category_id, title, description,
    pricing_mode, price, budget_min, budget_max, currency,
    location_mode, address, city, estimated_duration_minutes,
    status, cover_image_url, view_count, application_count,
    service_window_start, service_window_end, expires_at
)
SELECT
    'OFFER', user_account.id, worker.id, category.id,
    seed.title, seed.description, seed.pricing_mode,
    seed.price, seed.budget_min, seed.budget_max, 'USD',
    seed.location_mode,
    CASE WHEN seed.location_mode = 'REMOTE' THEN NULL ELSE worker.address END,
    seed.city, seed.duration_minutes, seed.status,
    NULL,
    seed.view_count, 0,
    CASE WHEN seed.location_mode = 'REMOTE' THEN NULL
        ELSE CURRENT_TIMESTAMP + INTERVAL '12 hours' END,
    CASE WHEN seed.location_mode = 'REMOTE' THEN NULL
        ELSE CURRENT_TIMESTAMP + INTERVAL '45 days' END,
    CURRENT_TIMESTAMP + seed.expiry_days * INTERVAL '1 day'
FROM offer_seed seed
JOIN users user_account ON user_account.email = seed.worker_email
JOIN worker_profiles worker ON worker.user_id = user_account.id
JOIN categories category ON category.code = seed.category_code;

WITH request_seed (
    client_email, category_code, title, description, pricing_mode,
    price, budget_min, budget_max, location_mode, address, city,
    duration_minutes, status, view_count, expiry_days
) AS (
    VALUES
        ('john@example.com', 'HOME_CLEANING', 'Deep Clean Apartment', 'Need a deep clean before family arrives this weekend.', 'BUDGET_RANGE', NULL, 140.00, 210.00, 'ON_SITE', '950 W Fulton Market, Chicago, IL', 'Chicago', 240, 'MATCHED', 91, 14),
        ('jane@example.com', 'PLUMBING', 'Fix Kitchen Sink', 'Kitchen sink drains slowly and the trap may need replacement.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', '1800 N Halsted St, Chicago, IL', 'Chicago', 120, 'OPEN', 43, 14),
        ('jane@example.com', 'MOVING', 'Help Move Studio', 'Loading, short-distance transport, and unloading for a studio apartment.', 'BUDGET_RANGE', NULL, 190.00, 280.00, 'ON_SITE', '1200 W Madison St, Chicago, IL', 'Chicago', 240, 'MATCHED', 112, 21),
        ('john@example.com', 'DELIVERY', 'Same Day Parcel Run', 'Pick up a boxed monitor downtown and deliver it to Lincoln Square.', 'FIXED', 42.00, NULL, NULL, 'ON_SITE', '233 S Wacker Dr, Chicago, IL', 'Chicago', 90, 'OPEN', 76, 7),
        ('john@example.com', 'PET_CARE', 'Weekend Pet Sitting', 'Two daily visits for a friendly cat over a long weekend.', 'FIXED', 96.00, NULL, NULL, 'ON_SITE', '2100 W Division St, Chicago, IL', 'Chicago', 180, 'MATCHED', 129, 14),
        ('jane@example.com', 'TUTORING', 'Algebra Tutor Needed', 'Remote help preparing for an algebra midterm.', 'BUDGET_RANGE', NULL, 45.00, 75.00, 'REMOTE', NULL, NULL, 75, 'OPEN', 68, 21),
        ('john@example.com', 'CHILDCARE', 'Background-Checked Babysitter', 'Occasional evening childcare for two school-age children.', 'BUDGET_RANGE', NULL, 90.00, 150.00, 'ON_SITE', '4300 N Damen Ave, Chicago, IL', 'Chicago', 240, 'DRAFT', 0, 30),
        ('jane@example.com', 'HANDYMAN', 'Assemble New Furniture', 'Assemble a bed frame and two bookshelves and remove packaging.', 'FIXED', 95.00, NULL, NULL, 'ON_SITE', '600 S Dearborn St, Chicago, IL', 'Chicago', 150, 'MATCHED', 104, 14),
        ('john@example.com', 'TECH_SUPPORT', 'Remote Laptop Recovery', 'Laptop fails after an update; need remote diagnosis and file recovery.', 'OPEN_BID', NULL, NULL, NULL, 'REMOTE', NULL, NULL, 120, 'OPEN', 157, 10),
        ('jane@example.com', 'EVENT_HELP', 'Event Setup Crew', 'Two people needed for nonprofit event setup and guest registration.', 'BUDGET_RANGE', NULL, 180.00, 280.00, 'ON_SITE', '78 E Washington St, Chicago, IL', 'Chicago', 300, 'OPEN', 63, 21),
        ('john@example.com', 'SPORTS_COACHING', 'Beginner Tennis Coach', 'Looking for three beginner lessons at a public court.', 'BUDGET_RANGE', NULL, 120.00, 180.00, 'ON_SITE', '2045 N Lincoln Park W, Chicago, IL', 'Chicago', 180, 'CLOSED', 55, 10),
        ('jane@example.com', 'ELECTRICAL', 'Licensed Outlet Repair', 'Two outlets stopped working; licensed electrician required.', 'BUDGET_RANGE', NULL, 100.00, 180.00, 'ON_SITE', '3200 N Lake Shore Dr, Chicago, IL', 'Chicago', 120, 'OPEN', 87, 14),
        ('john@example.com', 'PERSONAL_CARE', 'At-Home Hair Styling', 'Styling for a photo session; request no longer needed.', 'FIXED', 90.00, NULL, NULL, 'ON_SITE', '1000 N State St, Chicago, IL', 'Chicago', 90, 'CANCELLED', 34, 10),
        ('jane@example.com', 'HOME_CLEANING', 'Recurring Office Cleaning', 'Weekly evening cleaning for a small office suite.', 'OPEN_BID', NULL, NULL, NULL, 'ON_SITE', '200 W Adams St, Chicago, IL', 'Chicago', 180, 'EXPIRED', 101, -4),
        ('john@example.com', 'DELIVERY', 'Cross-Town Delivery', 'Several boxed donations need transport to a community center.', 'BUDGET_RANGE', NULL, 55.00, 95.00, 'ON_SITE', '1530 W 17th St, Chicago, IL', 'Chicago', 120, 'OPEN', 49, 10)
)
INSERT INTO tickets (
    kind, author_id, worker_id, category_id, title, description,
    pricing_mode, price, budget_min, budget_max, currency,
    location_mode, address, city, estimated_duration_minutes,
    status, cover_image_url, view_count, application_count,
    service_window_start, service_window_end, expires_at
)
SELECT
    'REQUEST', user_account.id, NULL, category.id,
    seed.title, seed.description, seed.pricing_mode,
    seed.price, seed.budget_min, seed.budget_max, 'USD',
    seed.location_mode, seed.address, seed.city,
    seed.duration_minutes, seed.status,
    NULL,
    seed.view_count, 0,
    CASE WHEN seed.location_mode = 'REMOTE' THEN NULL
        ELSE CURRENT_TIMESTAMP + INTERVAL '12 hours' END,
    CASE WHEN seed.location_mode = 'REMOTE' THEN NULL
        ELSE CURRENT_TIMESTAMP + INTERVAL '45 days' END,
    CURRENT_TIMESTAMP + seed.expiry_days * INTERVAL '1 day'
FROM request_seed seed
JOIN users user_account ON user_account.email = seed.client_email
JOIN categories category ON category.code = seed.category_code;

WITH application_seed (
    ticket_title, applicant_email, proposed_amount, message,
    status, start_in_days, duration_hours
) AS (
    VALUES
        ('Licensed Electrical Troubleshooting', 'jane@example.com', 120.00, 'The proposed time works for me.', 'ACCEPTED', 2, 2),
        ('Same-Day Local Delivery', 'john@example.com', 35.00, 'I can meet at the pickup entrance.', 'ACCEPTED', 1, 1),
        ('Patient Math Tutoring', 'jane@example.com', 55.00, 'I would like one session before the exam.', 'ACCEPTED', 3, 1),
        ('Verified Childcare Support', 'jane@example.com', 110.00, 'Evening coverage from five to nine.', 'ACCEPTED', 4, 4),
        ('Remote Computer Tune-Up', 'john@example.com', 65.00, 'Remote access is available after work.', 'ACCEPTED', 1, 2),
        ('Deep Clean Apartment', 'alice@spa.com', 185.00, 'I can bring supplies and complete the full checklist.', 'ACCEPTED', 2, 4),
        ('Help Move Studio', 'noah@swiftmove.example', 240.00, 'Two movers and a cargo van are included.', 'ACCEPTED', 5, 4),
        ('Weekend Pet Sitting', 'emma@carecircle.example', 96.00, 'I can cover both daily visits.', 'ACCEPTED', 6, 3),
        ('Assemble New Furniture', 'oliver@techhand.example', 95.00, 'Tools and packaging removal are included.', 'ACCEPTED', 3, 3),
        ('Deep Clean Apartment', 'sofia@freshstart.example', 195.00, 'Available Saturday morning.', 'REJECTED', 2, 4),
        ('Help Move Studio', 'oliver@techhand.example', 220.00, 'I can help load and unload.', 'REJECTED', 5, 4),
        ('Licensed Outlet Repair', 'liam@brightwire.example', 145.00, 'Licensed and available tomorrow.', 'PENDING', 1, 2),
        ('Same Day Parcel Run', 'noah@swiftmove.example', 42.00, 'I can complete this route this afternoon.', 'PENDING', 1, 2),
        ('Fix Kitchen Sink', 'oliver@techhand.example', 115.00, 'I can inspect the trap and fixture.', 'PENDING', 2, 2),
        ('Algebra Tutor Needed', 'ava@learnplay.example', 60.00, 'I can share a practice set afterward.', 'PENDING', 3, 2),
        ('Event Setup Crew', 'sofia@freshstart.example', 230.00, 'Two helpers are available.', 'WITHDRAWN', 7, 5),
        ('Cross-Town Delivery', 'noah@swiftmove.example', 75.00, 'Cargo space is available.', 'PENDING', 2, 2),
        ('Weekly Home Refresh', 'john@example.com', 85.00, 'Interested in a recurring schedule.', 'PENDING', 4, 3),
        ('Home Wi-Fi Optimization', 'jane@example.com', 135.00, 'Hybrid troubleshooting works for me.', 'PENDING', 2, 2),
        ('Recurring Office Cleaning', 'sofia@freshstart.example', 165.00, 'The listing expired before a decision was made.', 'EXPIRED', 2, 3)
)
INSERT INTO applications (
    ticket_id, applicant_id, proposed_amount, message,
    proposed_start, proposed_end, status
)
SELECT
    ticket.id, applicant.id, seed.proposed_amount, seed.message,
    CURRENT_TIMESTAMP + seed.start_in_days * INTERVAL '1 day',
    CURRENT_TIMESTAMP + seed.start_in_days * INTERVAL '1 day'
        + seed.duration_hours * INTERVAL '1 hour',
    seed.status
FROM application_seed seed
JOIN tickets ticket ON ticket.title = seed.ticket_title
JOIN users applicant ON applicant.email = seed.applicant_email;

UPDATE tickets ticket
SET application_count = counts.application_count
FROM (
    SELECT ticket_id, COUNT(*)::INTEGER AS application_count
    FROM applications
    GROUP BY ticket_id
) counts
WHERE counts.ticket_id = ticket.id;

WITH engagement_seed (
    ticket_title, applicant_email, client_email, worker_email,
    status, amount, note
) AS (
    VALUES
        ('Deep Clean Apartment', 'alice@spa.com', 'john@example.com', 'alice@spa.com', 'ACCEPTED', 185.00, 'Payment intent created; awaiting confirmation.'),
        ('Patient Math Tutoring', 'jane@example.com', 'jane@example.com', 'ava@learnplay.example', 'ACCEPTED', 55.00, 'Payment attempt failed; client may retry.'),
        ('Licensed Electrical Troubleshooting', 'jane@example.com', 'jane@example.com', 'liam@brightwire.example', 'FUNDED', 120.00, 'Funds secured; visit is scheduled.'),
        ('Same-Day Local Delivery', 'john@example.com', 'john@example.com', 'noah@swiftmove.example', 'IN_PROGRESS', 35.00, 'Package picked up and in transit.'),
        ('Verified Childcare Support', 'jane@example.com', 'jane@example.com', 'emma@carecircle.example', 'DELIVERED', 110.00, 'Service delivered; waiting for client approval.'),
        ('Remote Computer Tune-Up', 'john@example.com', 'john@example.com', 'oliver@techhand.example', 'COMPLETED', 65.00, 'Approved after successful remote tune-up.'),
        ('Help Move Studio', 'noah@swiftmove.example', 'jane@example.com', 'noah@swiftmove.example', 'DISPUTED', 240.00, 'Client reported damage to a lamp during transport.'),
        ('Weekend Pet Sitting', 'emma@carecircle.example', 'john@example.com', 'emma@carecircle.example', 'CANCELLED', 96.00, 'Cancelled before funding because travel plans changed.'),
        ('Assemble New Furniture', 'oliver@techhand.example', 'jane@example.com', 'oliver@techhand.example', 'REFUNDED', 95.00, 'Delivered work was disputed; the administrator resolved for the client and issued a full refund.')
)
INSERT INTO engagements (
    client_id, worker_id, ticket_id, application_id,
    status, amount, notes, idempotency_key,
    accepted_at, started_at, completed_at, cancelled_at, cancellation_reason,
    scheduled_start, scheduled_end, funded_at, delivered_at,
    approved_at, disputed_at, dispute_reason, created_at, updated_at
)
SELECT
    client_user.id,
    worker.id,
    ticket.id,
    application.id,
    seed.status,
    seed.amount,
    seed.note,
    'seed-engagement-' || LOWER(REPLACE(seed.status, '_', '-')) || '-' || ticket.id,
    CURRENT_TIMESTAMP - INTERVAL '7 days',
    CASE WHEN seed.status IN ('IN_PROGRESS', 'DELIVERED', 'COMPLETED', 'DISPUTED', 'REFUNDED')
        THEN CURRENT_TIMESTAMP - INTERVAL '5 days' END,
    CASE WHEN seed.status = 'COMPLETED'
        THEN CURRENT_TIMESTAMP - INTERVAL '2 days' END,
    CASE WHEN seed.status = 'CANCELLED'
        THEN CURRENT_TIMESTAMP - INTERVAL '2 days' END,
    CASE seed.status
        WHEN 'CANCELLED' THEN 'Client plans changed before payment.'
        ELSE NULL
    END,
    CURRENT_TIMESTAMP + INTERVAL '2 days',
    CURRENT_TIMESTAMP + INTERVAL '2 days 3 hours',
    CASE WHEN seed.status IN ('FUNDED', 'IN_PROGRESS', 'DELIVERED', 'COMPLETED', 'DISPUTED', 'REFUNDED')
        THEN CURRENT_TIMESTAMP - INTERVAL '6 days' END,
    CASE WHEN seed.status IN ('DELIVERED', 'COMPLETED', 'DISPUTED', 'REFUNDED')
        THEN CURRENT_TIMESTAMP - INTERVAL '3 days' END,
    CASE WHEN seed.status = 'COMPLETED'
        THEN CURRENT_TIMESTAMP - INTERVAL '2 days' END,
    CASE WHEN seed.status IN ('DISPUTED', 'REFUNDED')
        THEN CURRENT_TIMESTAMP - INTERVAL '2 days' END,
    CASE
        WHEN seed.status = 'DISPUTED'
            THEN 'A lamp was reported damaged during the move.'
        WHEN seed.status = 'REFUNDED'
            THEN 'The delivered assembly did not meet the agreed requirements.'
        ELSE NULL
    END,
    CURRENT_TIMESTAMP - INTERVAL '8 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM engagement_seed seed
JOIN tickets ticket ON ticket.title = seed.ticket_title
JOIN users applicant_user ON applicant_user.email = seed.applicant_email
JOIN applications application
    ON application.ticket_id = ticket.id
   AND application.applicant_id = applicant_user.id
   AND application.status = 'ACCEPTED'
JOIN users client_user ON client_user.email = seed.client_email
JOIN users worker_user ON worker_user.email = seed.worker_email
JOIN worker_profiles worker ON worker.user_id = worker_user.id;

WITH payment_seed (
    ticket_title, status, request_id, payment_intent_id, charge_id,
    provider_status, failure_message, currency
) AS (
    VALUES
        ('Deep Clean Apartment', 'PENDING', 'seed-pay-pending', 'pi_mock_seed_pending', NULL, 'requires_confirmation', NULL, 'USD'),
        ('Patient Math Tutoring', 'FAILED', 'seed-pay-failed', 'pi_mock_seed_failed', NULL, 'requires_payment_method', 'The test payment method was declined.', 'USD'),
        ('Licensed Electrical Troubleshooting', 'SUCCEEDED', 'seed-pay-funded', 'pi_mock_seed_funded', 'ch_mock_seed_funded', 'succeeded', NULL, 'USD'),
        ('Same-Day Local Delivery', 'SUCCEEDED', 'seed-pay-progress', 'pi_mock_seed_progress', 'ch_mock_seed_progress', 'succeeded', NULL, 'USD'),
        ('Verified Childcare Support', 'SUCCEEDED', 'seed-pay-delivered', 'pi_mock_seed_delivered', 'ch_mock_seed_delivered', 'succeeded', NULL, 'USD'),
        ('Remote Computer Tune-Up', 'SUCCEEDED', 'seed-pay-completed', 'pi_mock_seed_completed', 'ch_mock_seed_completed', 'succeeded', NULL, 'USD'),
        ('Help Move Studio', 'SUCCEEDED', 'seed-pay-disputed', 'pi_mock_seed_disputed', 'ch_mock_seed_disputed', 'succeeded', NULL, 'USD'),
        ('Assemble New Furniture', 'REFUNDED', 'seed-pay-refunded', 'pi_mock_seed_refunded', 'ch_mock_seed_refunded', 'refunded', NULL, 'USD')
)
INSERT INTO payments (
    engagement_id, request_id, amount, status, paid_at,
    payment_intent_id, charge_id, provider_status, failure_message, currency,
    created_at, updated_at
)
SELECT
    engagement.id, seed.request_id, engagement.amount, seed.status,
    CASE WHEN seed.status IN ('SUCCEEDED', 'REFUNDED')
        THEN CURRENT_TIMESTAMP - INTERVAL '6 days' END,
    seed.payment_intent_id, seed.charge_id, seed.provider_status,
    seed.failure_message, seed.currency,
    CURRENT_TIMESTAMP - INTERVAL '7 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM payment_seed seed
JOIN tickets ticket ON ticket.title = seed.ticket_title
JOIN engagements engagement ON engagement.ticket_id = ticket.id;

INSERT INTO refunds (
    engagement_id, payment_id, amount, reason, status,
    refunded_at, provider_refund_id, created_at, updated_at
)
SELECT
    engagement.id,
    payment.id,
    engagement.amount,
    'Dispute resolved in the client''s favor.',
    'COMPLETED',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    're_mock_seed_refunded',
    CURRENT_TIMESTAMP - INTERVAL '2 days',
    CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM engagements engagement
JOIN tickets ticket ON ticket.id = engagement.ticket_id
JOIN payments payment ON payment.engagement_id = engagement.id
WHERE ticket.title = 'Assemble New Furniture';

INSERT INTO settlement_batches (
    batch_id, status, total_count, success_count, failed_count,
    total_amount, started_at, completed_at, created_at
) VALUES (
    'seed-batch-completed', 'COMPLETED', 1, 1, 0,
    58.50, CURRENT_TIMESTAMP - INTERVAL '30 hours',
    CURRENT_TIMESTAMP - INTERVAL '29 hours',
    CURRENT_TIMESTAMP - INTERVAL '31 hours'
);

INSERT INTO settlements (
    engagement_id, total_price, platform_fee, provider_payout,
    status, settled_at, batch_id, processed_at, created_at, updated_at
)
SELECT
    engagement.id, engagement.amount, 6.50, 58.50,
    'COMPLETED', CURRENT_TIMESTAMP - INTERVAL '29 hours',
    'seed-batch-completed', CURRENT_TIMESTAMP - INTERVAL '29 hours',
    CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '29 hours'
FROM engagements engagement
JOIN tickets ticket ON ticket.id = engagement.ticket_id
WHERE ticket.title = 'Remote Computer Tune-Up';

WITH completed_engagement AS (
    SELECT engagement.id, engagement.client_id, worker_user.id AS worker_user_id
    FROM engagements engagement
    JOIN tickets ticket ON ticket.id = engagement.ticket_id
    JOIN worker_profiles worker ON worker.id = engagement.worker_id
    JOIN users worker_user ON worker_user.id = worker.user_id
    WHERE ticket.title = 'Remote Computer Tune-Up'
)
INSERT INTO reviews (
    engagement_id, reviewer_id, reviewee_id, direction,
    rating, comment, created_at, updated_at
)
SELECT id, client_id, worker_user_id, 'CLIENT_TO_WORKER', 5,
       'Clear explanations, quick turnaround, and the laptop is running smoothly.',
       CURRENT_TIMESTAMP - INTERVAL '20 hours', CURRENT_TIMESTAMP - INTERVAL '20 hours'
FROM completed_engagement
UNION ALL
SELECT id, worker_user_id, client_id, 'WORKER_TO_CLIENT', 4,
       'Responsive client with clear access instructions and expectations.',
       CURRENT_TIMESTAMP - INTERVAL '18 hours', CURRENT_TIMESTAMP - INTERVAL '18 hours'
FROM completed_engagement;

UPDATE worker_profiles worker
SET completed_jobs = (
        SELECT COUNT(*)::INTEGER
        FROM engagements engagement
        WHERE engagement.worker_id = worker.id
          AND engagement.status = 'COMPLETED'
    ),
    rating = COALESCE((
        SELECT ROUND(AVG(review.rating), 2)
        FROM reviews review
        JOIN users reviewee ON reviewee.id = review.reviewee_id
        WHERE reviewee.id = worker.user_id
          AND review.direction = 'CLIENT_TO_WORKER'
    ), 0.00),
    review_count = (
        SELECT COUNT(*)::INTEGER
        FROM reviews review
        WHERE review.reviewee_id = worker.user_id
          AND review.direction = 'CLIENT_TO_WORKER'
    );

UPDATE users user_account
SET client_rating = COALESCE((
        SELECT ROUND(AVG(review.rating), 2)
        FROM reviews review
        WHERE review.reviewee_id = user_account.id
          AND review.direction = 'WORKER_TO_CLIENT'
    ), 0.00),
    client_review_count = (
        SELECT COUNT(*)::INTEGER
        FROM reviews review
        WHERE review.reviewee_id = user_account.id
          AND review.direction = 'WORKER_TO_CLIENT'
    );

INSERT INTO audit_logs (
    entity_type, entity_id, action, actor_type, actor_id, details
)
SELECT
    'ENGAGEMENT', engagement.id, 'ENGAGEMENT_CREATED', 'CLIENT', engagement.client_id,
    JSONB_BUILD_OBJECT('ticketId', engagement.ticket_id, 'amount', engagement.amount)
FROM engagements engagement
JOIN tickets ticket ON ticket.id = engagement.ticket_id
WHERE ticket.title = 'Remote Computer Tune-Up'
UNION ALL
SELECT
    'ENGAGEMENT', engagement.id, 'PAYMENT_CONFIRMED', 'CLIENT', engagement.client_id,
    JSONB_BUILD_OBJECT('amount', engagement.amount)
FROM engagements engagement
JOIN tickets ticket ON ticket.id = engagement.ticket_id
WHERE ticket.title = 'Licensed Electrical Troubleshooting'
UNION ALL
SELECT
    'ENGAGEMENT', engagement.id, 'ENGAGEMENT_DISPUTED', 'CLIENT', engagement.client_id,
    JSONB_BUILD_OBJECT('reason', engagement.dispute_reason)
FROM engagements engagement
JOIN tickets ticket ON ticket.id = engagement.ticket_id
WHERE ticket.title = 'Help Move Studio';

INSERT INTO webhook_events (
    provider, event_id, event_type, payload, processed_at
)
SELECT
    'STRIPE', 'evt_seed_payment_succeeded', 'payment_intent.succeeded',
    JSONB_BUILD_OBJECT(
        'id', 'evt_seed_payment_succeeded',
        'type', 'payment_intent.succeeded',
        'paymentIntentId', payment.payment_intent_id
    ),
    CURRENT_TIMESTAMP - INTERVAL '6 days'
FROM payments payment
WHERE payment.payment_intent_id = 'pi_mock_seed_completed';
