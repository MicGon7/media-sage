package com.mediasage.di

import com.mediasage.feature.briefing.AndroidBriefingNotificationScheduler
import com.mediasage.feature.briefing.BriefingNotificationScheduler
import org.koin.dsl.module

val notificationModule = module {
    single<BriefingNotificationScheduler> { AndroidBriefingNotificationScheduler(get()) }
}
