package com.mediasage.analyst.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Establishes the Analyst's read connection to the Supabase Postgres `jobs` table.
 *
 * The Analyst is a *consumer* of pipeline data: it reads the cross-run history that the
 * orchestrator (`:agent`) writes. Schema ownership stays with the orchestrator — this module
 * deliberately runs no migrations and creates no tables. It only verifies connectivity at
 * startup so a misconfigured `SUPABASE_DB_URL` fails fast and loudly rather than on the first
 * `/stats` request.
 */
object FeedbackDatabase {

    fun init(postgresUrl: String) {
        val uri = java.net.URI(postgresUrl)
        val (user, password) = uri.userInfo.split(":", limit = 2)
        Database.connect(
            url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require",
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        transaction { exec("SELECT 1") }
    }
}
