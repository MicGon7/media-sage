-- Run once in Supabase SQL Editor before deploying the Analyst with this version.
-- The empty-string default satisfies NOT NULL for existing rows; new rows get a populated value.

ALTER TABLE decision_scores ADD COLUMN recommendation TEXT NOT NULL DEFAULT '';
