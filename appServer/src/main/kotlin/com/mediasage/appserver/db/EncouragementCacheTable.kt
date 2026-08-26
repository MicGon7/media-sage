package com.mediasage.appserver.db

import org.jetbrains.exposed.sql.Table

/** Shared cache of Claude-generated encouragements, keyed by article URL, so the same
 * article never triggers a second Claude call across any user or device. */
object EncouragementCacheTable : Table("encouragement_cache") {
    val id = long("id").autoIncrement()
    val articleUrl = varchar("article_url", 1024).uniqueIndex()
    val summary = text("summary").nullable()
    val quoteText = text("quote_text")
    val quoteSource = varchar("quote_source", 512)
    val figureName = varchar("figure_name", 255)
    val figureRole = varchar("figure_role", 255)
    val scriptureReference = varchar("scripture_reference", 255)
    val scriptureText = text("scripture_text")
    val explanation = text("explanation")
    val connectionThemes = text("connection_themes")
    val matchTheme = varchar("match_theme", 255)
    val tone = varchar("tone", 32)
    val cachedAt = long("cached_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}
