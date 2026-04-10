-- V1__baseline.sql
-- Baseline schema for MasterChefCuts (SQL Server) — matches JPA entities as of initial deployment.
-- Flyway applies this only to NEW databases; existing ones are baselined at version 0.

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'participants')
CREATE TABLE participants (
    id NVARCHAR(255) NOT NULL PRIMARY KEY,
    first_name NVARCHAR(255),
    last_name NVARCHAR(255),
    email NVARCHAR(255) NOT NULL UNIQUE,
    password NVARCHAR(255) NOT NULL,
    phone NVARCHAR(255),
    role NVARCHAR(255),
    shop_name NVARCHAR(255),
    street NVARCHAR(255),
    apt NVARCHAR(255),
    city NVARCHAR(255),
    state NVARCHAR(255),
    zip_code NVARCHAR(255),
    status NVARCHAR(255),
    total_spent FLOAT NOT NULL DEFAULT 0,
    approved BIT NOT NULL DEFAULT 1,
    reset_token NVARCHAR(255),
    reset_token_expiry DATETIME2,
    email_verified BIT NOT NULL DEFAULT 0,
    verification_token NVARCHAR(255),
    notification_preference NVARCHAR(255),
    stripe_account_id NVARCHAR(255),
    stripe_onboarding_complete BIT NOT NULL DEFAULT 0
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'listings')
CREATE TABLE listings (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    farmer_id NVARCHAR(255) NOT NULL,
    animal_type NVARCHAR(255),
    breed NVARCHAR(255),
    weight_lbs FLOAT NOT NULL DEFAULT 0,
    price_per_lb FLOAT NOT NULL DEFAULT 0,
    source_farm NVARCHAR(255),
    description NVARCHAR(255),
    image_url NVARCHAR(512),
    zip_code NVARCHAR(255),
    status NVARCHAR(255),
    processing_date DATE,
    posted_at DATETIME2,
    fully_claimed_at DATETIME2,
    CONSTRAINT fk_listing_farmer FOREIGN KEY (farmer_id) REFERENCES participants(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'cuts')
CREATE TABLE cuts (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    label NVARCHAR(255),
    claimed BIT NOT NULL DEFAULT 0,
    claimed_by_id NVARCHAR(255),
    claimed_at DATETIME2,
    CONSTRAINT fk_cut_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_cut_claimed_by FOREIGN KEY (claimed_by_id) REFERENCES participants(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'claims')
CREATE TABLE claims (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    buyer_id NVARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    cut_id BIGINT NOT NULL,
    claimed_at DATETIME2,
    expires_at DATETIME2,
    paid BIT NOT NULL DEFAULT 0,
    CONSTRAINT fk_claim_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_claim_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_claim_cut FOREIGN KEY (cut_id) REFERENCES cuts(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'animal_requests')
CREATE TABLE animal_requests (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    buyer_id NVARCHAR(255) NOT NULL,
    animal_type NVARCHAR(255),
    breed NVARCHAR(255),
    description NVARCHAR(255),
    zip_code NVARCHAR(255),
    status NVARCHAR(255),
    created_at DATETIME2,
    fulfilled_by_farmer_id NVARCHAR(255),
    fulfilled_listing_id BIGINT,
    CONSTRAINT fk_areq_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_areq_farmer FOREIGN KEY (fulfilled_by_farmer_id) REFERENCES participants(id),
    CONSTRAINT fk_areq_listing FOREIGN KEY (fulfilled_listing_id) REFERENCES listings(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'animal_request_cuts')
CREATE TABLE animal_request_cuts (
    request_id BIGINT NOT NULL,
    cut_label NVARCHAR(255),
    CONSTRAINT fk_areqcuts_request FOREIGN KEY (request_id) REFERENCES animal_requests(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'notifications')
CREATE TABLE notifications (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    recipient_id NVARCHAR(255) NOT NULL,
    type NVARCHAR(255),
    title NVARCHAR(255),
    body NVARCHAR(1000),
    icon NVARCHAR(255),
    listing_id BIGINT,
    order_id NVARCHAR(255),
    is_read BIT NOT NULL DEFAULT 0,
    created_at DATETIME2,
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES participants(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'orders')
CREATE TABLE orders (
    id NVARCHAR(255) NOT NULL PRIMARY KEY,
    stripe_payment_intent_id NVARCHAR(255) UNIQUE,
    participant_id NVARCHAR(255),
    order_date NVARCHAR(255),
    paid_at NVARCHAR(255),
    status NVARCHAR(255),
    amount_cents BIGINT,
    currency NVARCHAR(255),
    total_amount FLOAT NOT NULL DEFAULT 0,
    items NVARCHAR(MAX),
    delivery_date NVARCHAR(255),
    notes NVARCHAR(255)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'cart_items')
CREATE TABLE cart_items (
    id NVARCHAR(255) NOT NULL PRIMARY KEY,
    product_id NVARCHAR(255),
    product_name NVARCHAR(255),
    order_id BIGINT,
    participant_id NVARCHAR(255),
    quantity INT NOT NULL DEFAULT 0,
    total_price FLOAT NOT NULL DEFAULT 0,
    price_per_lb FLOAT NOT NULL DEFAULT 0,
    added_date NVARCHAR(255)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'products')
CREATE TABLE products (
    id NVARCHAR(255) NOT NULL PRIMARY KEY,
    name NVARCHAR(255),
    product NVARCHAR(255),
    cut_type NVARCHAR(255),
    description NVARCHAR(255),
    price_per_lb FLOAT NOT NULL DEFAULT 0,
    stock_per_lb FLOAT NOT NULL DEFAULT 0,
    origin NVARCHAR(255),
    grade NVARCHAR(255),
    is_featured NVARCHAR(255)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'purchases')
CREATE TABLE purchases (
    id NVARCHAR(255) NOT NULL PRIMARY KEY,
    participant_id NVARCHAR(255),
    purchase_date NVARCHAR(255),
    total_amount FLOAT NOT NULL DEFAULT 0,
    items NVARCHAR(MAX),
    status NVARCHAR(255),
    notes NVARCHAR(255)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'reviews')
CREATE TABLE reviews (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    buyer_id NVARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    rating INT NOT NULL DEFAULT 0,
    comment NVARCHAR(1000),
    created_at DATETIME2,
    CONSTRAINT fk_review_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_review_listing FOREIGN KEY (listing_id) REFERENCES listings(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'waitlist_entries')
CREATE TABLE waitlist_entries (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    buyer_id NVARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    joined_at DATETIME2,
    CONSTRAINT fk_wl_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_wl_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT uk_waitlist_buyer_listing UNIQUE (buyer_id, listing_id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'webhook_events')
CREATE TABLE webhook_events (
    id BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
    stripe_event_id NVARCHAR(100) NOT NULL UNIQUE,
    event_type NVARCHAR(100),
    processed_at DATETIME2,
    payment_intent_id NVARCHAR(100),
    notes NVARCHAR(500)
);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_webhook_event_id')
CREATE INDEX idx_webhook_event_id ON webhook_events (stripe_event_id);
