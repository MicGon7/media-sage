package com.mediasage.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mediasage.data.crypto.ReflectionNoteCipher

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

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE day_assignment ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE day_assignment ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE sync_meta ADD COLUMN lastDayAssignmentSyncUserId TEXT")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE daily_reflection ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        // figureId used to be baked into the primary key, but it isn't portable across devices —
        // only epochDay/tone/theme need to be, since exactly one figure is ever locked per epochDay.
        connection.execSQL("UPDATE daily_reflection SET id = epochDay || '_' || tone || '_' || theme")
        connection.execSQL("ALTER TABLE sync_meta ADD COLUMN lastDailyReflectionSyncUserId TEXT")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(connection: SQLiteConnection) {
        // Defaults to 1 (synced) — a non-bookmarked cache row has nothing to push, and must
        // never look like a pending sync write the way a fresh MS-51/MS-664 row does.
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN pendingDelete INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE sync_meta ADD COLUMN lastSavedInsightSyncUserId TEXT")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(connection: SQLiteConnection) {
        // Defaults to 1 (synced) — a non-memorized quote-catalog row has nothing to push, and must
        // never look like a pending sync write the way a fresh memorize does.
        connection.execSQL("ALTER TABLE quotes ADD COLUMN memorized INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE quotes ADD COLUMN synced INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE sync_meta ADD COLUMN lastMemorizedQuoteSyncUserId TEXT")
        // MS-669: the Match/MatchDao/MatchRepository system was confirmed unused (zero call sites
        // outside its own DI wiring) and quotes.id was its only foreign-key reference.
        connection.execSQL("DROP TABLE IF EXISTS matches")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE headlines ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN headlineCategory TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE encouragements ADD COLUMN headlinePublishedAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE headlines ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS discovered_quotes " +
                "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, figureId INTEGER NOT NULL, " +
                "quoteText TEXT NOT NULL, source TEXT NOT NULL, themes TEXT NOT NULL, " +
                "synced INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(figureId) REFERENCES figures(id) ON DELETE CASCADE)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_discovered_quotes_figureId ON discovered_quotes(figureId)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_discovered_quotes_figureId_quoteText " +
                "ON discovered_quotes(figureId, quoteText)"
        )
        connection.execSQL("ALTER TABLE sync_meta ADD COLUMN lastDiscoveredQuoteSyncUserId TEXT")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE daily_reflection ADD COLUMN challenge TEXT")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS user_reflection_note " +
                "(id TEXT NOT NULL, noteText TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(id))"
        )
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        // isRead moves off the shared headlines cache into its own per-user table — headlines
        // gets wiped/reinserted independently of any account, so a column on it can never be
        // scoped to a user and previously leaked read status across accounts on the same device.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS read_headlines " +
                "(userId TEXT NOT NULL, url TEXT NOT NULL, PRIMARY KEY(userId, url))"
        )
        // Preserve existing read state under an anonymous bucket rather than silently dropping it —
        // per-account attribution only becomes possible going forward.
        connection.execSQL(
            "INSERT INTO read_headlines (userId, url) SELECT '', url FROM headlines WHERE isRead = 1"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS headlines_new " +
                "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, source TEXT NOT NULL, " +
                "url TEXT NOT NULL, imageUrl TEXT, publishedAt INTEGER NOT NULL, fetchedAt INTEGER NOT NULL, " +
                "snippet TEXT, category TEXT NOT NULL DEFAULT '')"
        )
        connection.execSQL(
            "INSERT INTO headlines_new (id, title, source, url, imageUrl, publishedAt, fetchedAt, snippet, category) " +
                "SELECT id, title, source, url, imageUrl, publishedAt, fetchedAt, snippet, category FROM headlines"
        )
        connection.execSQL("DROP TABLE headlines")
        connection.execSQL("ALTER TABLE headlines_new RENAME TO headlines")
    }
}

// Takes the cipher as a param (unlike the other migrations here) because it encrypts existing
// plaintext note rows in place rather than just running static SQL.
fun migration35To36(cipher: ReflectionNoteCipher): Migration = object : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        val plaintextNotesById = readPlaintextNotes(connection)
        plaintextNotesById.forEach { (id, plaintext) ->
            val statement = connection.prepare("UPDATE user_reflection_note SET noteText = ? WHERE id = ?")
            try {
                statement.bindText(1, cipher.encrypt(plaintext))
                statement.bindText(2, id)
                statement.step()
            } finally {
                statement.close()
            }
        }
    }

    private fun readPlaintextNotes(connection: SQLiteConnection): List<Pair<String, String>> {
        val notes = mutableListOf<Pair<String, String>>()
        val statement = connection.prepare("SELECT id, noteText FROM user_reflection_note")
        try {
            while (statement.step()) {
                notes += statement.getText(0) to statement.getText(1)
            }
        } finally {
            statement.close()
        }
        return notes
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(connection: SQLiteConnection) {
        // Composite (userId, id) key so two accounts on the same device can never read or
        // overwrite each other's note (MS-736) — mirrors read_headlines' (userId, url) fix for
        // the same class of leak (MS-734). Pre-existing notes predate any account association,
        // so they're bucketed under an anonymous '' userId, same precedent as MIGRATION_34_35 —
        // they become invisible once a real account is signed in, rather than visible to it.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS user_reflection_note_new " +
                "(userId TEXT NOT NULL, id TEXT NOT NULL, noteText TEXT NOT NULL, " +
                "updatedAtMillis INTEGER NOT NULL, synced INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(userId, id))"
        )
        connection.execSQL(
            "INSERT INTO user_reflection_note_new (userId, id, noteText, updatedAtMillis, synced) " +
                "SELECT '', id, noteText, updatedAtMillis, 0 FROM user_reflection_note"
        )
        connection.execSQL("DROP TABLE user_reflection_note")
        connection.execSQL("ALTER TABLE user_reflection_note_new RENAME TO user_reflection_note")
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
