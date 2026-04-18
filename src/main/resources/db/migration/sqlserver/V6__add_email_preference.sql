-- V6__add_email_preference.sql
-- Adds email_preference column to participants table.
-- Matches EmailPreference enum: ALL | IMPORTANT | NONE

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'email_preference')
    ALTER TABLE participants ADD email_preference NVARCHAR(20) NOT NULL DEFAULT 'ALL';
