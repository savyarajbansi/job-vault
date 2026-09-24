-- Optional seeker salary range used by explainable job matching.
ALTER TABLE users ADD COLUMN preferred_salary_min integer;
ALTER TABLE users ADD COLUMN preferred_salary_max integer;

ALTER TABLE users ADD CONSTRAINT chk_users_preferred_salary_range
    CHECK (preferred_salary_min IS NULL OR preferred_salary_max IS NULL
        OR preferred_salary_max >= preferred_salary_min);
