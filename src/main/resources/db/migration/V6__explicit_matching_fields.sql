-- Explicit experience/location fields for phase-1 data capture.

ALTER TABLE users ADD COLUMN preferred_location varchar(150);
ALTER TABLE users ADD COLUMN remote_ok boolean;
ALTER TABLE users ADD COLUMN years_experience integer;

ALTER TABLE jobs ADD COLUMN location varchar(150);
ALTER TABLE jobs ADD COLUMN remote_eligible boolean;
ALTER TABLE jobs ADD COLUMN min_experience_years integer;
