package com.mediasage.di

import com.mediasage.data.repository.FigureRepositoryImpl
import com.mediasage.data.repository.HeadlineRepositoryImpl
import com.mediasage.data.repository.MatchRepositoryImpl
import com.mediasage.data.repository.QuoteRepositoryImpl
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.MatchRepository
import com.mediasage.domain.repository.QuoteRepository
import org.koin.dsl.module

val sharedModule = module {
    // Repositories — interface bound to implementation
    single<FigureRepository> { FigureRepositoryImpl(get()) }
    single<QuoteRepository> { QuoteRepositoryImpl(get()) }
    single<HeadlineRepository> { HeadlineRepositoryImpl(get()) }
    single<MatchRepository> { MatchRepositoryImpl(get()) }
}
