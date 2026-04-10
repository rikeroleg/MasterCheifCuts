-- V1__baseline.sql
-- Baseline schema for MasterChefCuts (MySQL) — matches JPA entities as of initial deployment.
-- Flyway applies this only to NEW databases; existing ones are baselined at version 0.

CREATE TABLE IF NOT EXISTS participants (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    role VARCHAR(255),
    shop_name VARCHAR(255),
    street VARCHAR(255),
    apt VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    zip_code VARCHAR(255),
    status VARCHAR(255),
    total_spent DOUBLE NOT NULL DEFAULT 0,
    approved BIT NOT NULL DEFAULT 1,
    reset_token VARCHAR(255),
    reset_token_expiry DATETIME(6),
    email_verified BIT NOT NULL DEFAULT 0,
    verification_token VARCHAR(255),
    notification_preference VARCHAR(255),
    stripe_account_id VARCHAR(255),
    stripe_onboarding_complete BIT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS listings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    farmer_id VARCHAR(255) NOT NULL,
    animal_type VARCHAR(255),
    breed VARCHAR(255),
    weight_lbs DOUBLE NOT NULL DEFAULT 0,
    price_per_lb DOUBLE NOT NULL DEFAULT 0,
    source_farm VARCHAR(255),
    description VARCHAR(255),
    image_url VARCHAR(512),
    zip_code VARCHAR(255),
    status VARCHAR(255),
    processing_date DATE,
    posted_at DATETIME(6),
    fully_claimed_at DATETIME(6),
    CONSTRAINT fk_listing_farmer FOREIGN KEY (farmer_id) REFERENCES participants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cuts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    label VARCHAR(255),
    claimed BIT NOT NULL DEFAULT 0,
    claimed_by_id VARCHAR(255),
    claimed_at DATETIME(6),
    CONSTRAINT fk_cut_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_cut_claimed_by FOREIGN KEY (claimed_by_id) REFERENCES participants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS claims (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    cut_id BIGINT NOT NULL,
    claimed_at DATETIME(6),
    expires_at DATETIME(6),
    paid BIT NOT NULL DEFAULT 0,
    CONSTRAINT fk_claim_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_claim_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_claim_cut FOREIGN KEY (cut_id) REFERENCES cuts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS animal_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    animal_type VARCHAR(255),
    breed VARCHAR(255),
    description VARCHAR(255),
    zip_code VARCHAR(255),
    status VARCHAR(255),
    created_at DATETIME(6),
    fulfilled_by_farmer_id VARCHAR(255),
    fulfilled_listing_id BIGINT,
    CONSTRAINT fk_areq_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_areq_farmer FOREIGN KEY (fulfilled_by_farmer_id) REFERENCES participants(id),
    CONSTRAINT fk_areq_listing FOREIGN KEY (fulfilled_listing_id) REFERENCES listings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS animal_request_cuts (
    request_id BIGINT NOT NULL,
    cut_label VARCHAR(255),
    CONSTRAINT fk_areqcuts_request FOREIGN KEY (request_id) REFERENCES animal_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id VARCHAR(255) NOT NULL,
    type VARCHAR(255),
    title VARCHAR(255),
    body VARCHAR(1000),
    icon VARCHAR(255),
    listing_id BIGINT,
    order_id VARCHAR(255),
    is_read BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES participants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    participant_id VARCHAR(255),
    order_date VARCHAR(255),
    paid_at VARCHAR(255),
    status VARCHAR(255),
    amount_cents BIGINT,
    currency VARCHAR(255),
    total_amount DOUBLE NOT NULL DEFAULT 0,
    items TEXT,
    delivery_date VARCHAR(255),
    notes VARCHAR(255),
    payment_type VARCHAR(255),
    remaining_amount_cents BIGINT,
    balance_payment_intent_id VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cart_items (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    product_id VARCHAR(255),
    product_name VARCHAR(255),
    order_id BIGINT,
    participant_id VARCHAR(255),
    quantity INT NOT NULL DEFAULT 0,
    total_price DOUBLE NOT NULL DEFAULT 0,
    price_per_lb DOUBLE NOT NULL DEFAULT 0,
    added_date VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    product VARCHAR(255),
    cut_type VARCHAR(255),
    description VARCHAR(255),
    price_per_lb DOUBLE NOT NULL DEFAULT 0,
    stock_per_lb DOUBLE NOT NULL DEFAULT 0,
    origin VARCHAR(255),
    grade VARCHAR(255),
    is_featured VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchases (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    participant_id VARCHAR(255),
    purchase_date VARCHAR(255),
    total_amount DOUBLE NOT NULL DEFAULT 0,
    items TEXT,
    status VARCHAR(255),
    notes VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    rating INT NOT NULL DEFAULT 0,
    comment VARCHAR(1000),
    created_at DATETIME(6),
    CONSTRAINT fk_review_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_review_listing FOREIGN KEY (listing_id) REFERENCES listings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id VARCHAR(255) NOT NULL,
    listing_id BIGINT NOT NULL,
    joined_at DATETIME(6),
    CONSTRAINT fk_wl_buyer FOREIGN KEY (buyer_id) REFERENCES participants(id),
    CONSTRAINT fk_wl_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT uk_waitlist_buyer_listing UNIQUE (buyer_id, listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stripe_event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100),
    processed_at DATETIME(6),
    payment_intent_id VARCHAR(100),
    notes VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_webhook_event_id ON webhook_events (stripe_event_id);
