-- Resume enrichment and matching/audit tables

ALTER TABLE resumes
    ADD COLUMN parsed_text text;

ALTER TABLE resumes
    ADD COLUMN inferred_skills text;

ALTER TABLE resumes
    ADD COLUMN storage_type varchar(50);

ALTER TABLE resumes
    ADD COLUMN storage_key varchar(500);

CREATE TABLE match_results (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL,
    resume_id uuid NOT NULL,
    overall_score double precision NOT NULL,
    cosine_score double precision NOT NULL,
    skills_score double precision NOT NULL,
    experience_score double precision NOT NULL,
    location_score double precision NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_match_results_job_id
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_results_resume_id
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
    CONSTRAINT uq_match_results_job_resume UNIQUE (job_id, resume_id)
);

CREATE INDEX idx_match_results_job_id ON match_results (job_id);
CREATE INDEX idx_match_results_resume_id ON match_results (resume_id);

CREATE TABLE resume_parse_attempts (
    id uuid PRIMARY KEY,
    resume_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    error_code varchar(50),
    duration_ms integer,
    extracted_text_length integer,
    inferred_skill_count integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_resume_parse_attempts_resume_id
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE
);

CREATE INDEX idx_resume_parse_attempts_resume_id ON resume_parse_attempts (resume_id);
CREATE INDEX idx_resume_parse_attempts_status ON resume_parse_attempts (status);

CREATE TABLE match_attempts (
    id uuid PRIMARY KEY,
    job_id uuid,
    resume_id uuid,
    status varchar(20) NOT NULL,
    error_code varchar(50),
    duration_ms integer,
    result_count integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_match_attempts_job_id
        FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE SET NULL,
    CONSTRAINT fk_match_attempts_resume_id
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL
);

CREATE INDEX idx_match_attempts_job_id ON match_attempts (job_id);
CREATE INDEX idx_match_attempts_resume_id ON match_attempts (resume_id);
CREATE INDEX idx_match_attempts_status ON match_attempts (status);
