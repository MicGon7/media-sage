package com.mediasage.di

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.repository.WikipediaRepository
import com.mediasage.feature.figures.FigureDetailViewModel
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.headlinedetail.HeadlineDetailViewModel
import com.mediasage.feature.you.YouViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { HomeViewModel(get()) }
    viewModel { (headlineId: Long) -> HeadlineDetailViewModel(headlineId, get(), get()) }
    viewModel { FiguresViewModel(get<EncouragementDao>()) }
    viewModel { (figureName: String) -> FigureDetailViewModel(figureName, get<EncouragementDao>(), get<WikipediaRepository>()) }
    viewModel { YouViewModel() }
}

/** Temporary module that overrides MediaSageApi with mock data for demos. */
val mockApiModule = module {
    single<MediaSageApi> { MockMediaSageApi() }
}
