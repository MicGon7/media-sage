package com.mediasage.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mediasage.data.crypto.createReflectionNoteCipher

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<MediaSageDatabase> {
    val dbFile = context.getDatabasePath("mediasage.db")
    return Room.databaseBuilder<MediaSageDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
        .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, migration35To36(createReflectionNoteCipher()))
        .fallbackToDestructiveMigration(dropAllTables = true)
}
