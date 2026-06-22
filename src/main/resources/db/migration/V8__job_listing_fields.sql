-- Job listing enrichment: company name, salary range (USD), and education requirement.
-- company_name: nullable so existing rows are unaffected; employers fill it per posting.
-- salary_min / salary_max: integer cents could be used but plain USD amounts are fine for now.
-- education_requirement: constrained to a fixed vocabulary via CHECK to keep data consistent
--   for future filtering; NULL means "not specified" rather than "no requirement".

ALTER TABLE jobs ADD COLUMN company_name varchar(200);

ALTER TABLE jobs ADD COLUMN salary_min integer;
ALTER TABLE jobs ADD COLUMN salary_max integer;

ALTER TABLE jobs ADD COLUMN education_requirement varchar(50);

ALTER TABLE jobs ADD CONSTRAINT chk_jobs_salary_range
    CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min);

ALTER TABLE jobs ADD CONSTRAINT chk_jobs_education_requirement
    CHECK (education_requirement IN (
        'HIGH_SCHOOL',
        'BACHELORS',
        'MASTERS',
        'PHD'
    ));
