package com.mediasage.advisor

import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.net.URI

private val log = LoggerFactory.getLogger("AdvisorDatabase")

internal fun connectDatabase(dbUrl: String): Database {
    log.info("Connecting to Supabase Postgres")
    val uri = URI(dbUrl)
    val (user, password) = uri.userInfo.split(":", limit = 2)
    return Database.connect(
        url = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require",
        driver = "org.postgresql.Driver",
        user = user,
        password = password,
    )
}
