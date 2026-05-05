package com.mediasage.scripts.service

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File

private object FigureTable : Table("figures") {
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
    val updatedAt = long("updated_at").default(0L)

    override val primaryKey = PrimaryKey(id)
}

data class FigureRow(
    val id: Long,
    val name: String,
    val role: String,
    val century: String,
    val lifespan: String,
    val portraitUrl: String?
)

object ScriptsDatabase {

    fun init(dbPath: String) {
        val dbFile = File(dbPath)
        Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(FigureTable)
        }
    }

    fun fetchAllFigures(): List<FigureRow> = transaction {
        FigureTable.selectAll().map { row ->
            FigureRow(
                id = row[FigureTable.id],
                name = row[FigureTable.name],
                role = row[FigureTable.role],
                century = row[FigureTable.century],
                lifespan = row[FigureTable.lifespan],
                portraitUrl = row[FigureTable.portraitUrl]
            )
        }
    }

    fun updateFigurePortraitUrl(figureId: Long, url: String) = transaction {
        FigureTable.update({ FigureTable.id eq figureId }) {
            it[FigureTable.portraitUrl] = url
        }
    }
}
