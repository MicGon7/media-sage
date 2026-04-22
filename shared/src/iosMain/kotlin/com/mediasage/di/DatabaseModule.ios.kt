package com.mediasage.di

import com.mediasage.data.local.db.MediaSageDatabase
import com.mediasage.data.local.db.getDatabaseBuilder
import org.koin.dsl.module

val databaseModule = module {
    single<MediaSageDatabase> { getDatabaseBuilder().build() }
}
