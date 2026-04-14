-- V2__add_missing_tables.sql
-- Adds tables that were present as JPA entities but missing from V1 baseline.

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'audit_events')
CREATE TABLE audit_events (
    id          BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    actor_id    NVARCHAR(255),
    action      NVARCHAR(100) NOT NULL,
    target_id   NVARCHAR(255),
    [timestamp] DATETIME2     NOT NULL
);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_audit_actor' AND object_id = OBJECT_ID('audit_events'))
    CREATE INDEX idx_audit_actor  ON audit_events (actor_id);
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_audit_target' AND object_id = OBJECT_ID('audit_events'))
    CREATE INDEX idx_audit_target ON audit_events (target_id);
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_audit_ts' AND object_id = OBJECT_ID('audit_events'))
    CREATE INDEX idx_audit_ts     ON audit_events (timestamp);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'comments')
CREATE TABLE comments (
    id          BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    listing_id  BIGINT         NOT NULL,
    author_id   NVARCHAR(255)  NOT NULL,
    body        NVARCHAR(1000) NOT NULL,
    created_at  DATETIME2,
    CONSTRAINT fk_comment_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_comment_author  FOREIGN KEY (author_id)  REFERENCES participants(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'disputes')
CREATE TABLE disputes (
    id           BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    claim_id     BIGINT,
    listing_id   BIGINT,
    buyer_id     NVARCHAR(255)  NOT NULL,
    buyer_name   NVARCHAR(255),
    farmer_id    NVARCHAR(255),
    farmer_name  NVARCHAR(255),
    type         NVARCHAR(255)  NOT NULL,
    description  NVARCHAR(2000),
    status       NVARCHAR(255)  NOT NULL DEFAULT 'OPEN',
    resolution   NVARCHAR(2000),
    created_at   DATETIME2      NOT NULL,
    resolved_at  DATETIME2
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'messages')
CREATE TABLE messages (
    id           BIGINT         NOT NULL IDENTITY(1,1) PRIMARY KEY,
    sender_id    NVARCHAR(255)  NOT NULL,
    receiver_id  NVARCHAR(255)  NOT NULL,
    content      NVARCHAR(2000) NOT NULL,
    is_read      BIT            NOT NULL DEFAULT 0,
    sent_at      DATETIME2,
    CONSTRAINT fk_message_sender   FOREIGN KEY (sender_id)   REFERENCES participants(id),
    CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id) REFERENCES participants(id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'referrals')
CREATE TABLE referrals (
    id           BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    referrer_id  NVARCHAR(255) NOT NULL,
    referred_id  NVARCHAR(255) NOT NULL UNIQUE,
    created_at   DATETIME2
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'waitlist_entries')
CREATE TABLE waitlist_entries (
    id          BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    buyer_id    NVARCHAR(255) NOT NULL,
    listing_id  BIGINT        NOT NULL,
    joined_at   DATETIME2,
    CONSTRAINT fk_waitlist_buyer   FOREIGN KEY (buyer_id)   REFERENCES participants(id),
    CONSTRAINT fk_waitlist_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT uq_waitlist_buyer_listing UNIQUE (buyer_id, listing_id)
);

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'webhook_events')
CREATE TABLE webhook_events (
    id                  BIGINT        NOT NULL IDENTITY(1,1) PRIMARY KEY,
    stripe_event_id     NVARCHAR(100) NOT NULL UNIQUE,
    event_type          NVARCHAR(100),
    processed_at        DATETIME2,
    payment_intent_id   NVARCHAR(100),
    notes               NVARCHAR(500)
);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_webhook_event_id' AND object_id = OBJECT_ID('webhook_events'))
    CREATE UNIQUE INDEX idx_webhook_event_id ON webhook_events (stripe_event_id);
