package com.mediasage.di

import com.mediasage.data.local.db.MediaSageDatabase
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.MediaSageApiImpl
import com.mediasage.data.remote.createHttpClient
import com.mediasage.data.repository.AuthRepositoryImpl
import com.mediasage.data.repository.DailyReflectionRemoteDataSource
import com.mediasage.data.repository.DailyReflectionRepositoryImpl
import com.mediasage.data.repository.DayAssignmentRemoteDataSource
import com.mediasage.data.repository.DayAssignmentRepositoryImpl
import com.mediasage.data.repository.DiscoveredQuoteRemoteDataSource
import com.mediasage.data.repository.EncouragementRepositoryImpl
import com.mediasage.data.repository.FigureRepositoryImpl
import com.mediasage.data.repository.HeadlineRepositoryImpl
import com.mediasage.data.repository.MemorizedQuoteRemoteDataSource
import com.mediasage.data.repository.PostgrestDailyReflectionRemoteDataSource
import com.mediasage.data.repository.PostgrestDayAssignmentRemoteDataSource
import com.mediasage.data.repository.PostgrestDiscoveredQuoteRemoteDataSource
import com.mediasage.data.repository.PostgrestMemorizedQuoteRemoteDataSource
import com.mediasage.data.repository.PostgrestProfileRemoteDataSource
import com.mediasage.data.repository.PostgrestSavedInsightRemoteDataSource
import com.mediasage.data.repository.ProfileRemoteDataSource
import com.mediasage.data.repository.ProfileRepositoryImpl
import com.mediasage.data.repository.QuoteRepositoryImpl
import com.mediasage.data.repository.SavedInsightRemoteDataSource
import com.mediasage.data.repository.UserReflectionNoteRepositoryImpl
import com.mediasage.data.repository.WikipediaRepositoryImpl
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.ProfileRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.repository.UserReflectionNoteRepository
import com.mediasage.domain.repository.WikipediaRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import com.mediasage.domain.usecase.GetHeadlinesFeedUseCase
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
                install(Postgrest)
            }
        }
        single<DayAssignmentRemoteDataSource> { PostgrestDayAssignmentRemoteDataSource(get()) }
        single<DailyReflectionRemoteDataSource> { PostgrestDailyReflectionRemoteDataSource(get()) }
        single<SavedInsightRemoteDataSource> { PostgrestSavedInsightRemoteDataSource(get()) }
        single<MemorizedQuoteRemoteDataSource> { PostgrestMemorizedQuoteRemoteDataSource(get()) }
        single<DiscoveredQuoteRemoteDataSource> { PostgrestDiscoveredQuoteRemoteDataSource(get()) }
        single<ProfileRemoteDataSource> { PostgrestProfileRemoteDataSource(get()) }
    }

    // HTTP client for communicating with the Media Sage server
    single { createHttpClient() }

    // API service — single interface for all server endpoints
    single<MediaSageApi> { MediaSageApiImpl(get(), serverBaseUrl) }

    // DAOs — extracted from the database instance provided by the platform module
    single { get<MediaSageDatabase>().figureDao() }
    single { get<MediaSageDatabase>().quoteDao() }
    single { get<MediaSageDatabase>().headlineDao() }
    single { get<MediaSageDatabase>().encouragementDao() }
    single { get<MediaSageDatabase>().syncMetaDao() }
    single { get<MediaSageDatabase>().dailyReflectionDao() }
    single { get<MediaSageDatabase>().dayAssignmentDao() }
    single { get<MediaSageDatabase>().discoveredQuoteDao() }
    single { get<MediaSageDatabase>().userReflectionNoteDao() }

    // Repositories — interface bound to implementation
    single<FigureRepository> { FigureRepositoryImpl(get(), get(), get()) }
    single<QuoteRepository> { QuoteRepositoryImpl(get(), get(), getOrNull(), get(), get()) }
    single<HeadlineRepository> { HeadlineRepositoryImpl(get(), get(), get()) }
    single<EncouragementRepository> {
        EncouragementRepositoryImpl(get(), get(), get(), getOrNull(), get(), get(), get(), getOrNull())
    }
    single<WikipediaRepository> { WikipediaRepositoryImpl(get()) }
    single<DailyReflectionRepository> {
        DailyReflectionRepositoryImpl(get(), get(), get(), getOrNull(), get(), get())
    }
    single<AuthRepository> { AuthRepositoryImpl(getOrNull<SupabaseClient>()) }
    single<ProfileRepository> { ProfileRepositoryImpl(getOrNull()) }
    single<DayAssignmentRepository> {
        DayAssignmentRepositoryImpl(get(), get(), get(), get(), getOrNull(), get(), get())
    }
    single<UserReflectionNoteRepository> { UserReflectionNoteRepositoryImpl(get()) }

    // Domain use cases — combine/transform data from multiple repositories (NiA domain layer)
    single { GetReaderCalendarUseCase(get(), get(), get(), get()) }
    single { GetDayDetailUseCase(get()) }
    single { GetHeadlinesFeedUseCase(get(), get()) }
}
