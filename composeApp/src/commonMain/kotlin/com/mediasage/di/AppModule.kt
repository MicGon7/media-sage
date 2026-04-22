package com.mediasage.di

import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.match.MatchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { HomeViewModel(get()) }
    viewModel { (headlineId: Long) -> MatchViewModel(headlineId, get(), get()) }
}
