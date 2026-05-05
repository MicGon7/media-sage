package com.mediasage.di

import com.mediasage.AppViewModel
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import com.mediasage.feature.bookmarks.BookmarksViewModel
import com.mediasage.feature.figures.FigureDetailViewModel
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.history.HistoryViewModel
import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.headlinedetail.HeadlineDetailViewModel
import com.mediasage.feature.you.YouViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { AppViewModel(get<FigureRepository>()) }
    viewModel { HomeViewModel(get<HeadlineRepository>(), get<PinnedFigureRepository>(), get<DailyReflectionRepository>(), get<FigureRepository>()) }
    viewModel { (articleUrl: String) -> HeadlineDetailViewModel(articleUrl, get(), get()) }
    viewModel { FiguresViewModel(get<FigureRepository>(), get<EncouragementDao>(), get<PinnedFigureRepository>()) }
    viewModel { (figureId: Long) -> FigureDetailViewModel(figureId, get<FigureRepository>(), get<EncouragementRepository>(), get<PinnedFigureRepository>()) }
    viewModel { YouViewModel() }
    viewModel { HistoryViewModel(get<EncouragementDao>()) }
    viewModel { BookmarksViewModel(get<EncouragementDao>()) }
}

/** Temporary module that overrides MediaSageApi with mock data for demos. */
val mockApiModule = module {
    single<MediaSageApi> { MockMediaSageApi() }
}
