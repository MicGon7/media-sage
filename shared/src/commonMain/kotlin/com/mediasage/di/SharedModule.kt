package com.mediasage.di

import com.mediasage.data.local.db.MediaSageDatabase
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.MediaSageApiImpl
import com.mediasage.data.remote.createHttpClient
import com.mediasage.data.repository.EncouragementRepositoryImpl
import com.mediasage.data.repository.FigureRepositoryImpl
import com.mediasage.data.repository.HeadlineRepositoryImpl
import com.mediasage.data.repository.MatchRepositoryImpl
import com.mediasage.data.repository.QuoteRepositoryImpl
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.MatchRepository
import com.mediasage.domain.repository.QuoteRepository
import org.koin.dsl.module

fun sharedModule(serverBaseUrl: String = "http://10.0.2.2:8080") = module {
    // HTTP client for communicating with the Media Sage server
    single { createHttpClient() }

    // API service — single interface for all server endpoints
    single<MediaSageApi> { MediaSageApiImpl(get(), serverBaseUrl) }

    // DAOs — extracted from the database instance provided by the platform module
    single { get<MediaSageDatabase>().figureDao() }
    single { get<MediaSageDatabase>().quoteDao() }
    single { get<MediaSageDatabase>().headlineDao() }
    single { get<MediaSageDatabase>().matchDao() }
    single { get<MediaSageDatabase>().encouragementDao() }

    // Repositories — interface bound to implementation
    single<FigureRepository> { FigureRepositoryImpl(get()) }
    single<QuoteRepository> { QuoteRepositoryImpl(get()) }
    single<HeadlineRepository> { HeadlineRepositoryImpl(get(), get()) }
    single<MatchRepository> { MatchRepositoryImpl(get()) }
    single<EncouragementRepository> { EncouragementRepositoryImpl(get(), get()) }
}
