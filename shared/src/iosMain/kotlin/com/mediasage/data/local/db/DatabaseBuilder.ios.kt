package com.mediasage.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSHomeDirectory

fun getDatabaseBuilder(): RoomDatabase.Builder<MediaSageDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/mediasage.db"
    return Room.databaseBuilder<MediaSageDatabase>(
        name = dbFilePath
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_12_13)
        .fallbackToDestructiveMigration(dropAllTables = true)
}
