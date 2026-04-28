-- V8: Add verification_token_expiry column to participants
-- Required for email verification link time-bounding (24-hour TTL).
ALTER TABLE participants ADD verification_token_expiry DATETIME2 NULL;
