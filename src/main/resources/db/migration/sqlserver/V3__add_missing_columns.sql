-- V3__add_missing_columns.sql
-- Adds columns present in JPA entities but missing from existing tables.

-- cuts.weight_lbs: added to Cut entity after V1 baseline
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'cuts' AND COLUMN_NAME = 'weight_lbs'
)
    ALTER TABLE cuts ADD weight_lbs FLOAT;
