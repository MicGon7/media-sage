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
     * Add new ALTER TABLE / CREATE OR REPLACE statements here. Column drops are rare and must be
     * idempotent (`DROP COLUMN IF EXISTS`) after every reader has been removed — see
     * [dropFailedGateColumn].
     */
    private fun org.jetbrains.exposed.sql.Transaction.migrate() {
        addWorkerMetricColumns()
        addModelVersionColumn()
        dropFailedGateColumn()
        renamePromptToPayload()
        createJobDurationsView()
        createTranscriptsTable()
        dropDecisionScoresTable()
    }

    /** Worker efficiency metric columns. */
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

    /** Model tracking (`model_version`). A sibling `failed_gate` column was retired — see
     * [dropFailedGateColumn]. */
    private fun org.jetbrains.exposed.sql.Transaction.addModelVersionColumn() = exec(
        "ALTER TABLE jobs ADD COLUMN IF NOT EXISTS model_version TEXT"
    )

    /**
     * Drops the retired `failed_gate` column. Run death (`status = FAILED`) is not a gate failure,
     * and the hardened pipeline suppresses gate failures by design, so the column was never
     * populated (0/17 FAILED rows). Idempotent — safe once every reader has been removed.
     */
    private fun org.jetbrains.exposed.sql.Transaction.dropFailedGateColumn() =
        exec("ALTER TABLE jobs DROP COLUMN IF EXISTS failed_gate")

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

    /**
     * Drops the dead `decision_scores` table. Per-job decision scoring was retired; this removes
     * the now-orphaned table so a fresh boot no longer recreates it and the deployed
     * Supabase instance is cleaned up on the next orchestrator deploy. Idempotent.
     */
    private fun org.jetbrains.exposed.sql.Transaction.dropDecisionScoresTable() =
        exec("DROP TABLE IF EXISTS decision_scores")

    /** Human-readable worker transcripts, one row per job. */
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
