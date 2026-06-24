-- Run once in Supabase SQL Editor before deploying the Analyst with ANTHROPIC_API_KEY set.
-- Schema is owned by the Analyst; the orchestrator never reads or writes this table.

CREATE TABLE IF NOT EXISTS decision_scores (
    job_id          UUID        NOT NULL REFERENCES jobs(job_id),
    decision_index  INT         NOT NULL,
    criterion       TEXT        NOT NULL,
    score           INT         NOT NULL CHECK (score BETWEEN 1 AND 5),
    rationale       TEXT        NOT NULL,
    PRIMARY KEY (job_id, decision_index, criterion)
);

CREATE INDEX IF NOT EXISTS decision_scores_job_id_idx ON decision_scores (job_id);
