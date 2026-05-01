package com.mediasage.server.db

import org.jetbrains.exposed.sql.Table

object FigureTable : Table("figures") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255).uniqueIndex()
    val category = varchar("category", 64)
    val century = varchar("century", 32)
    val role = varchar("role", 255).default("")
    val lifespan = varchar("lifespan", 64).default("")
    val bio = text("bio").default("")
    val themes = text("themes").default("")
    val portraitUrl = varchar("portrait_url", 512).nullable()
    val isEnabled = bool("is_enabled").default(true)

    override val primaryKey = PrimaryKey(id)
}
