package com.mediasage.di

import com.mediasage.data.local.db.MediaSageDatabase
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.MediaSageApiImpl
import com.mediasage.data.remote.createHttpClient
import com.mediasage.data.repository.AuthRepositoryImpl
import com.mediasage.data.repository.DailyReflectionRepositoryImpl
import com.mediasage.data.repository.EncouragementRepositoryImpl
import com.mediasage.data.repository.FigureRepositoryImpl
import com.mediasage.data.repository.PinnedFigureRepositoryImpl
import com.mediasage.data.repository.WikipediaRepositoryImpl
import com.mediasage.data.repository.HeadlineRepositoryImpl
import com.mediasage.data.repository.MatchRepositoryImpl
import com.mediasage.data.repository.QuoteRepositoryImpl
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.MatchRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.repository.WikipediaRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import org.koin.dsl.module

fun sharedModule(
    serverBaseUrl: String = "http://10.0.2.2:8080",
    supabaseUrl: String = "",
    supabaseAnonKey: String = ""
) = module {
    // Supabase client — only registered when credentials are configured
    if (supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()) {
        single<SupabaseClient> {
            createSupabaseClient(supabaseUrl, supabaseAnonKey) {
                install(Auth)
            }
        }
    }

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
    single { get<MediaSageDatabase>().syncMetaDao() }
    single { get<MediaSageDatabase>().dailyReflectionDao() }
    single { get<MediaSageDatabase>().pinnedFigureDao() }

    // Repositories — interface bound to implementation
    single<FigureRepository> { FigureRepositoryImpl(get(), get(), get()) }
    single<QuoteRepository> { QuoteRepositoryImpl(get()) }
    single<HeadlineRepository> { HeadlineRepositoryImpl(get(), get()) }
    single<MatchRepository> { MatchRepositoryImpl(get()) }
    single<EncouragementRepository> { EncouragementRepositoryImpl(get(), get(), get()) }
    single<WikipediaRepository> { WikipediaRepositoryImpl(get()) }
    single<DailyReflectionRepository> { DailyReflectionRepositoryImpl(get(), get()) }
    single<PinnedFigureRepository> { PinnedFigureRepositoryImpl(get()) }
    single<AuthRepository> { AuthRepositoryImpl(getOrNull<SupabaseClient>()) }
}
