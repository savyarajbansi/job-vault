ALTER TABLE candidate_match_notifications
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE candidate_match_notifications
    ADD CONSTRAINT chk_candidate_match_notification_status
    CHECK (status IN ('PENDING', 'ACCEPTED'));

ALTER TABLE notifications
    ADD COLUMN candidate_match_notification_id uuid;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_candidate_match
    FOREIGN KEY (candidate_match_notification_id)
    REFERENCES candidate_match_notifications (id)
    ON DELETE SET NULL;

CREATE INDEX idx_notifications_candidate_match
    ON notifications (candidate_match_notification_id);
