package com.mediasage.orchestrator.db

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
        addEnvStartupColumn() // MS-399 — must run before the view, which selects env_startup_ms
        createJobDurationsView()
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

    /**
     * MS-399: Environment startup time — wall-clock from dispatch (`started_at`) to the worker
     * container's first log line (Cloud Run cold start + worker image pull), in milliseconds.
     * Computed orchestrator-side and recorded on completion; the dominant overhead for short jobs.
     */
    private fun org.jetbrains.exposed.sql.Transaction.addEnvStartupColumn() = exec(
        """
        ALTER TABLE jobs
          ADD COLUMN IF NOT EXISTS env_startup_ms BIGINT
        """.trimIndent()
    )

    private fun org.jetbrains.exposed.sql.Transaction.createJobDurationsView() = exec(
        """
        CREATE OR REPLACE VIEW job_durations AS
        SELECT
          job_id,
          ticket_key,
          status,
          EXTRACT(EPOCH FROM (completed_at - started_at))::int AS duration_seconds,
          created_at,
          started_at,
          completed_at,
          env_startup_ms
        FROM jobs
        WHERE started_at IS NOT NULL
        """.trimIndent()
    )
}
