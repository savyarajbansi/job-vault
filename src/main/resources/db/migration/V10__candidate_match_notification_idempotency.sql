CREATE TABLE candidate_match_notifications (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL,
    seeker_id uuid NOT NULL,
    employer_id uuid NOT NULL,
    score double precision NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_candidate_match_notification_job
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_match_notification_seeker
        FOREIGN KEY (seeker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidate_match_notification_employer
        FOREIGN KEY (employer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_candidate_match_notification_job_seeker UNIQUE (job_id, seeker_id)
);

CREATE INDEX idx_candidate_match_notifications_employer
    ON candidate_match_notifications (employer_id, created_at DESC);
