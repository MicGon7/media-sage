package com.mediasage.data.local.db

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

fun getDatabaseBuilder(): RoomDatabase.Builder<MediaSageDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Documents/mediasage.db"
    return Room.databaseBuilder<MediaSageDatabase>(
        name = dbFilePath
    ).fallbackToDestructiveMigration(dropAllTables = true)
}
