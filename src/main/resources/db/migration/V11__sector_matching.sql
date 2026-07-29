-- Sector score and availability are persisted with cached match explanations.

ALTER TABLE match_results ADD COLUMN sector_score double precision NOT NULL DEFAULT 0;
ALTER TABLE match_results ADD COLUMN sector_available boolean NOT NULL DEFAULT false;
