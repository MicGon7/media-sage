package com.mediasage.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<MediaSageDatabase> {
    val dbFile = context.getDatabasePath("mediasage.db")
    return Room.databaseBuilder<MediaSageDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
        .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
        .fallbackToDestructiveMigration(dropAllTables = true)
}
