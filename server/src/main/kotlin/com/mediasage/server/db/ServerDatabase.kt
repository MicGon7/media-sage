package com.mediasage.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object ServerDatabase {

    fun init(dbPath: String = "mediasage-server.db") {
        val dbFile = File(dbPath)
        Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(FigureTable, QuoteTable)
        }
    }
}
