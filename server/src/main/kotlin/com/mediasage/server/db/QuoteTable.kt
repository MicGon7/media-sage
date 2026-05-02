package com.mediasage.server.db

import org.jetbrains.exposed.sql.Table

object QuoteTable : Table("quotes") {
    val id = long("id").autoIncrement()
    val figureId = long("figure_id")
    val text = text("text")
    val sourceText = varchar("source", 512).default("")
    val themes = text("themes").default("")
    val verified = bool("verified").default(false)

    override val primaryKey = PrimaryKey(id)
}
