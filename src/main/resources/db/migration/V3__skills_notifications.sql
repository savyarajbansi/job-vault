-- Skills, job skill mappings, notifications, and simple preference fields

ALTER TABLE jobs
    ADD COLUMN sector varchar(100);

ALTER TABLE users
    ADD COLUMN preferred_sector varchar(100);

CREATE TABLE skills (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL UNIQUE
);

CREATE TABLE job_required_skills (
    job_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT fk_job_required_skills_job_id
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_required_skills_skill_id
        FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);

CREATE TABLE job_preferred_skills (
    job_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT fk_job_preferred_skills_job_id
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_preferred_skills_skill_id
        FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
);

CREATE INDEX idx_job_required_skills_skill_id ON job_required_skills (skill_id);
CREATE INDEX idx_job_preferred_skills_skill_id ON job_preferred_skills (skill_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    message text NOT NULL,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id, is_read);

UPDATE jobs
SET status = 'ACTIVE'
WHERE status = 'ACTIVE';
