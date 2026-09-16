DELETE FROM notifications
WHERE type IN ('CANDIDATE_SHORTLISTED', 'SHORTLIST_ACCEPTED');

DROP INDEX IF EXISTS idx_notifications_candidate_match;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS fk_notifications_candidate_match;

ALTER TABLE notifications
    DROP COLUMN IF EXISTS candidate_match_notification_id;

DROP TABLE IF EXISTS candidate_match_notifications;
