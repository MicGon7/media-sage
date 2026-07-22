package com.mediasage.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS user_preferences " +
                "(id INTEGER NOT NULL, selected_lens TEXT NOT NULL DEFAULT 'NEWS', PRIMARY KEY(id))"
        )
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE daily_reflection ADD COLUMN theme TEXT NOT NULL DEFAULT 'NEWS'"
        )
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS daily_reflection_new " +
                "(id TEXT NOT NULL, figureId INTEGER NOT NULL, epochDay INTEGER NOT NULL, " +
                "tone TEXT NOT NULL, theme TEXT NOT NULL DEFAULT 'NEWS', " +
                "scriptureReference TEXT NOT NULL, scriptureText TEXT NOT NULL, " +
                "insight TEXT NOT NULL, implication TEXT NOT NULL, inspiration TEXT NOT NULL, " +
                "sources TEXT NOT NULL, PRIMARY KEY(id))"
        )
        connection.execSQL(
            "INSERT INTO daily_reflection_new " +
                "(id, figureId, epochDay, tone, theme, scriptureReference, scriptureText, " +
                "insight, implication, inspiration, sources) " +
                "SELECT id, figureId, epochDay, tone, theme, scriptureReference, scriptureText, " +
                "reflection, '', '', sources FROM daily_reflection"
        )
        connection.execSQL("DROP TABLE daily_reflection")
        connection.execSQL("ALTER TABLE daily_reflection_new RENAME TO daily_reflection")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE day_assignment ADD COLUMN lens TEXT")
        connection.execSQL("DROP TABLE IF EXISTS user_preferences")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS schedule_override " +
                "(epochDay INTEGER NOT NULL, figureId INTEGER NOT NULL, PRIMARY KEY(epochDay))"
        )
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS schedule_override")
    }
}

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

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE figures ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS day_assignment " +
                "(dayOfWeek INTEGER NOT NULL, figureId INTEGER NOT NULL, PRIMARY KEY(dayOfWeek))"
        )
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS pinned_figure")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_quotes_figureId_text` ON `quotes` (`figureId`, `text`)"
        )
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
