package com.mediasage.appserver.db

import org.jetbrains.exposed.sql.Table

/** Tracks how many Claude API calls the encourage endpoint has made per calendar day (UTC). */
object ClaudeCallLimitTable : Table("claude_call_limit") {
    val id = long("id").autoIncrement()
    val callDate = varchar("call_date", 10).uniqueIndex()
    val callCount = integer("call_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
