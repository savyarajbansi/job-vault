-- Versioned match-result cache metadata and explainability fields.

ALTER TABLE match_results ADD COLUMN algorithm_version varchar(50) NOT NULL DEFAULT 'legacy';
ALTER TABLE match_results ADD COLUMN corpus_fingerprint varchar(128) NOT NULL DEFAULT 'legacy';
ALTER TABLE match_results ADD COLUMN job_revision timestamptz;
ALTER TABLE match_results ADD COLUMN resume_revision timestamptz;
ALTER TABLE match_results ADD COLUMN seeker_revision timestamptz;
ALTER TABLE match_results ADD COLUMN missing_skills text NOT NULL DEFAULT '';
ALTER TABLE match_results ADD COLUMN cosine_available boolean NOT NULL DEFAULT true;
ALTER TABLE match_results ADD COLUMN skills_available boolean NOT NULL DEFAULT true;
ALTER TABLE match_results ADD COLUMN experience_available boolean NOT NULL DEFAULT true;
ALTER TABLE match_results ADD COLUMN location_available boolean NOT NULL DEFAULT true;
ALTER TABLE match_results ADD COLUMN computed_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX idx_match_results_resume_score
    ON match_results (resume_id, overall_score DESC);
CREATE INDEX idx_match_results_job_score
    ON match_results (job_id, overall_score DESC);
