package com.mediasage.di

import com.mediasage.feature.briefing.BriefingNotificationScheduler
import com.mediasage.feature.briefing.IosBriefingNotificationScheduler
import org.koin.dsl.module

val notificationModule = module {
    single<BriefingNotificationScheduler> { IosBriefingNotificationScheduler() }
}
