package com.mediasage.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_meta " +
                "(id INTEGER NOT NULL, last_figure_sync_at INTEGER, PRIMARY KEY(id))"
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN figureId INTEGER")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS daily_reflection " +
                "(id TEXT NOT NULL, figureId INTEGER NOT NULL, epochDay INTEGER NOT NULL, " +
                "tone TEXT NOT NULL, scriptureReference TEXT NOT NULL, scriptureText TEXT NOT NULL, " +
                "reflection TEXT NOT NULL, sources TEXT NOT NULL, PRIMARY KEY(id))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS pinned_figure " +
                "(id INTEGER NOT NULL, figureId INTEGER, PRIMARY KEY(id))"
        )
    }
}
