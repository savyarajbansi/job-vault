-- Keep one current resume per seeker and migrate free-text preference fields.

ALTER TABLE users ADD COLUMN preferred_sectors text;
ALTER TABLE users ADD COLUMN work_mode varchar(20);
ALTER TABLE jobs ADD COLUMN sector_tags text;
ALTER TABLE jobs ADD COLUMN work_mode varchar(20);

UPDATE users
SET preferred_sectors = preferred_sector
WHERE preferred_sector IS NOT NULL AND preferred_sectors IS NULL;

UPDATE users
SET work_mode = CASE
    WHEN remote_ok IS TRUE THEN 'REMOTE'
    WHEN remote_ok IS FALSE THEN 'ON_SITE'
    ELSE NULL
END
WHERE work_mode IS NULL;

UPDATE jobs
SET sector_tags = sector
WHERE sector IS NOT NULL AND sector_tags IS NULL;

UPDATE jobs
SET work_mode = CASE
    WHEN remote_eligible IS TRUE THEN 'REMOTE'
    WHEN remote_eligible IS FALSE THEN 'ON_SITE'
    ELSE NULL
END
WHERE work_mode IS NULL;

-- Preserve the newest parsed resume for each seeker, then the newest remaining row.
DELETE FROM match_results
WHERE resume_id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY seeker_id
                   ORDER BY (processing_status = 'PARSED') DESC,
                            parsed_at DESC NULLS LAST,
                            created_at DESC,
                            id DESC) AS rn
        FROM resumes
    ) ranked
    WHERE rn > 1
);

DELETE FROM resume_parse_attempts
WHERE resume_id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY seeker_id
                   ORDER BY (processing_status = 'PARSED') DESC,
                            parsed_at DESC NULLS LAST,
                            created_at DESC,
                            id DESC) AS rn
        FROM resumes
    ) ranked
    WHERE rn > 1
);

DELETE FROM resumes
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY seeker_id
                   ORDER BY (processing_status = 'PARSED') DESC,
                            parsed_at DESC NULLS LAST,
                            created_at DESC,
                            id DESC) AS rn
        FROM resumes
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX uq_resumes_seeker_id ON resumes (seeker_id);
