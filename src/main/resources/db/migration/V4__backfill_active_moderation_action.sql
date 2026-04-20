-- Backfill legacy rows where ACTIVE jobs have null moderation action.
UPDATE jobs
SET moderation_action = 'APPROVED'
WHERE status = 'ACTIVE'
  AND moderation_action IS NULL;
