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
