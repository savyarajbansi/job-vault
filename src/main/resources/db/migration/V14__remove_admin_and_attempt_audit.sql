-- The matching project computes results on demand. Remove the old admin,
-- cache, and request-audit storage that is no longer part of the workflow.

DROP TABLE IF EXISTS match_attempts;
DROP TABLE IF EXISTS resume_parse_attempts;
DROP TABLE IF EXISTS match_results;

ALTER TABLE jobs DROP CONSTRAINT IF EXISTS fk_jobs_moderated_by;
ALTER TABLE jobs DROP COLUMN IF EXISTS moderation_action;
ALTER TABLE jobs DROP COLUMN IF EXISTS moderation_reason;
ALTER TABLE jobs DROP COLUMN IF EXISTS moderated_at;
ALTER TABLE jobs DROP COLUMN IF EXISTS moderated_by;

DELETE FROM user_roles
WHERE role_id IN (SELECT id FROM roles WHERE name = 'ADMIN');

DELETE FROM roles WHERE name = 'ADMIN';
