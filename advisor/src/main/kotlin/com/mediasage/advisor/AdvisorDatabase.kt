package com.mediasage.advisor

import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AdvisorDatabase")

internal fun connectDatabase(dbUrl: String): Database {
    log.info("Connecting to Supabase Postgres")
    return Database.connect(url = dbUrl, driver = "org.postgresql.Driver")
}
