-- Baseline migration: database-wide extensions only.
-- Business tables are introduced in later milestones, one migration per change.

-- Case-insensitive text (emails, skill aliases).
CREATE EXTENSION IF NOT EXISTS citext;

-- Trigram indexes for job title/description search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
