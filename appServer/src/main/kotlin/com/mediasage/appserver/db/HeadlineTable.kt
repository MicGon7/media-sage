package com.mediasage.appserver.db

import org.jetbrains.exposed.sql.Table

object HeadlineTable : Table("headlines") {
    val id = long("id").autoIncrement()
    val uuid = varchar("uuid", 64)
    val category = varchar("category", 32)
    val title = varchar("title", 512)
    val description = text("description").default("")
    val snippet = text("snippet").default("")
    val url = varchar("url", 1024)
    val imageUrl = varchar("image_url", 1024).default("")
    val publishedAt = varchar("published_at", 64).default("")
    val sourceText = varchar("source", 255).default("")
    val fetchedAt = long("fetched_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}
