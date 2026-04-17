-- V5__add_refresh_token_columns.sql
-- Adds refresh token columns to participants table for persistent login support.

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'refresh_token')
    ALTER TABLE participants ADD refresh_token NVARCHAR(255);

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'refresh_token_expiry')
    ALTER TABLE participants ADD refresh_token_expiry DATETIME2;
