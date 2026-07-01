package com.mediasage.agentruntime.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

object AgentDatabase {

    fun init(postgresUrl: String) {
        val uri = java.net.URI(postgresUrl)
        val (user, password) = uri.userInfo.split(":", limit = 2)
        Database.connect(
            url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require",
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        transaction {
            exec("SELECT 1")
            migrate()
        }
    }

    /**
     * Idempotent schema migrations — safe to run on every startup.
     * Add new ALTER TABLE / CREATE OR REPLACE statements here; never drop columns.
     */
    private fun org.jetbrains.exposed.sql.Transaction.migrate() {
        addWorkerMetricColumns() // MS-210
        addFailureAttributionColumns() // MS-386
        renamePromptToPayload()
        createJobDurationsView()
        createTranscriptsTable() // MS-387
        createDecisionScoresTable()
    }

    /** MS-210: Worker efficiency metric columns. */
    private fun org.jetbrains.exposed.sql.Transaction.addWorkerMetricColumns() = exec(
        """
        ALTER TABLE jobs
          ADD COLUMN IF NOT EXISTS input_tokens INT,
          ADD COLUMN IF NOT EXISTS output_tokens INT,
          ADD COLUMN IF NOT EXISTS cache_read_tokens INT,
          ADD COLUMN IF NOT EXISTS cache_creation_tokens INT,
          ADD COLUMN IF NOT EXISTS total_cost_usd NUMERIC(10, 6),
          ADD COLUMN IF NOT EXISTS claude_duration_ms BIGINT,
          ADD COLUMN IF NOT EXISTS num_turns INT
        """.trimIndent()
    )

    /** MS-386: Failure attribution (`failed_gate`) + model tracking (`model_version`). */
    private fun org.jetbrains.exposed.sql.Transaction.addFailureAttributionColumns() = exec(
        """
        ALTER TABLE jobs
          ADD COLUMN IF NOT EXISTS failed_gate TEXT,
          ADD COLUMN IF NOT EXISTS model_version TEXT
        """.trimIndent()
    )

    /** Renames the `prompt` column to `payload` — idempotent via existence check. */
    private fun org.jetbrains.exposed.sql.Transaction.renamePromptToPayload() = exec(
        """
        DO ${'$'}${'$'}
        BEGIN
          IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'jobs' AND column_name = 'prompt'
          ) THEN
            ALTER TABLE jobs RENAME COLUMN prompt TO payload;
          END IF;
        END ${'$'}${'$'};
        """.trimIndent()
    )

    /** Decision scoring results, one row per (job, decision index, criterion). */
    private fun org.jetbrains.exposed.sql.Transaction.createDecisionScoresTable() = exec(
        """
        CREATE TABLE IF NOT EXISTS decision_scores (
            job_id          UUID REFERENCES jobs(job_id),
            decision_index  INT NOT NULL,
            criterion       TEXT NOT NULL,
            score           INT NOT NULL,
            rationale       TEXT NOT NULL,
            recommendation  TEXT NOT NULL,
            PRIMARY KEY (job_id, decision_index, criterion)
        )
        """.trimIndent()
    )

    /** MS-387: Human-readable worker transcripts, one row per job. */
    private fun org.jetbrains.exposed.sql.Transaction.createTranscriptsTable() = exec(
        """
        CREATE TABLE IF NOT EXISTS transcripts (
            job_id  UUID PRIMARY KEY REFERENCES jobs(job_id),
            content TEXT NOT NULL
        )
        """.trimIndent()
    )

    private fun org.jetbrains.exposed.sql.Transaction.createJobDurationsView() {
        // DROP + CREATE instead of CREATE OR REPLACE — Postgres forbids OR REPLACE when columns are removed
        exec("DROP VIEW IF EXISTS job_durations")
        exec(
            """
            CREATE VIEW job_durations AS
            SELECT
              job_id,
              ticket_key,
              status,
              EXTRACT(EPOCH FROM (completed_at - started_at))::int AS duration_seconds,
              created_at,
              started_at,
              completed_at
            FROM jobs
            WHERE started_at IS NOT NULL
            """.trimIndent()
        )
    }
}
