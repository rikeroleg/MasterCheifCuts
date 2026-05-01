-- V9__fix_email_preference_constraint.sql
-- Drops the auto-named SQL Server default constraint on email_preference so
-- Hibernate's ddl-auto=update can alter the column without error.

DECLARE @constraint NVARCHAR(256)
SELECT @constraint = dc.name
FROM   sys.default_constraints dc
JOIN   sys.columns c ON c.default_object_id = dc.object_id
JOIN   sys.tables  t ON t.object_id = c.object_id
WHERE  t.name = 'participants'
  AND  c.name = 'email_preference'

IF @constraint IS NOT NULL
    EXEC ('ALTER TABLE participants DROP CONSTRAINT [' + @constraint + ']')

-- Ensure column is wide enough for the longest enum value (max 9: IMPORTANT)
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_NAME = 'participants' AND COLUMN_NAME = 'email_preference')
BEGIN
    ALTER TABLE participants ALTER COLUMN email_preference NVARCHAR(255) NOT NULL
    -- Re-add default without a named constraint so SQL Server auto-names it
    ALTER TABLE participants ADD DEFAULT 'ALL' FOR email_preference
END
