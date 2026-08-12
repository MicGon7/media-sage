package com.mediasage.di

import com.mediasage.AppViewModel
import com.mediasage.data.ThemePreferencesRepository
import com.mediasage.data.AuthPreferencesRepository
import com.mediasage.data.HeadlineCategoryPreferencesRepository
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.ProfileRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import com.mediasage.domain.usecase.GetHeadlinesFeedUseCase
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import com.mediasage.feature.bookmarks.BookmarksViewModel
import com.mediasage.feature.login.LoginViewModel
import com.mediasage.feature.settings.SettingsViewModel
import com.mediasage.feature.figures.FigureDetailViewModel
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.history.HistoryViewModel
import com.mediasage.feature.briefing.BriefingViewModel
import com.mediasage.feature.headlines.HeadlinesViewModel
import com.mediasage.feature.quotes.QuotesViewModel
import com.mediasage.feature.headlinedetail.HeadlineDetailViewModel
import com.mediasage.feature.daydetail.DayDetailViewModel
import com.mediasage.feature.you.ReaderHistoryViewModel
import com.mediasage.feature.you.ReaderViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel {
        AppViewModel(
            get<FigureRepository>(),
            get<DayAssignmentRepository>(),
            get<DailyReflectionRepository>(),
            get<EncouragementRepository>(),
            get<QuoteRepository>(),
            get<ThemePreferencesRepository>(),
            get<AuthRepository>(),
        )
    }
    viewModel { BriefingViewModel(get<DayAssignmentRepository>(), get<DailyReflectionRepository>(), get<FigureRepository>(), get<HeadlineRepository>()) }
    viewModel {
        HeadlinesViewModel(
            get<HeadlineRepository>(),
            get<EncouragementRepository>(),
            get<GetHeadlinesFeedUseCase>(),
            get<HeadlineCategoryPreferencesRepository>(),
        )
    }
    viewModel { (articleUrl: String) -> HeadlineDetailViewModel(articleUrl, get(), get(), get(), get()) }
    viewModel { FiguresViewModel(get<FigureRepository>(), get<EncouragementRepository>(), get<DayAssignmentRepository>()) }
    viewModel { (figureId: Long) ->
        FigureDetailViewModel(
            figureId,
            get<FigureRepository>(),
            get<EncouragementRepository>(),
            get<DayAssignmentRepository>(),
            get<DailyReflectionRepository>(),
            get<QuoteRepository>(),
        )
    }
    viewModel { QuotesViewModel(get<QuoteRepository>(), get<FigureRepository>()) }
    viewModel { LoginViewModel(get<AuthRepository>(), get<AuthPreferencesRepository>(), get<ProfileRepository>()) }
    viewModel { SettingsViewModel(get<AuthRepository>(), get<ThemePreferencesRepository>()) }
    viewModel {
        ReaderViewModel(
            get<GetReaderCalendarUseCase>(),
            get<DayAssignmentRepository>(),
            get<AuthRepository>(),
            get<DailyReflectionRepository>(),
        )
    }
    viewModel { ReaderHistoryViewModel(get<GetReaderCalendarUseCase>(), get<DailyReflectionRepository>()) }
    viewModel { (epochDay: Long, figureName: String?, figureImageUrl: String?) ->
        DayDetailViewModel(epochDay, figureName, figureImageUrl, get<GetDayDetailUseCase>())
    }
    viewModel { HistoryViewModel(get<EncouragementRepository>()) }
    viewModel { BookmarksViewModel(get<EncouragementRepository>()) }
}

/** Temporary module that overrides MediaSageApi with mock data for demos. */
val mockApiModule = module {
    single<MediaSageApi> { MockMediaSageApi() }
}
