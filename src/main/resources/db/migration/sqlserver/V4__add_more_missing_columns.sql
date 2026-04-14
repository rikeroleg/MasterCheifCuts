-- V4__add_more_missing_columns.sql
-- Adds columns present in JPA entities but missing from existing tables.

-- orders: delivery address snapshot fields added after V1 baseline
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'delivery_street')
    ALTER TABLE orders ADD delivery_street NVARCHAR(255);

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'delivery_city')
    ALTER TABLE orders ADD delivery_city NVARCHAR(255);

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'delivery_state')
    ALTER TABLE orders ADD delivery_state NVARCHAR(255);

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'delivery_zip')
    ALTER TABLE orders ADD delivery_zip NVARCHAR(255);

-- participants: farmer profile enrichment fields added after V1 baseline
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'bio')
    ALTER TABLE participants ADD bio NVARCHAR(500);

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'certifications')
    ALTER TABLE participants ADD certifications NVARCHAR(500);
