package com.mediasage.agent.db

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
}
