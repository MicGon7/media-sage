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
        // MS-210: Worker efficiency metric columns
        exec(
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
        exec(
            """
            CREATE OR REPLACE VIEW job_durations AS
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
