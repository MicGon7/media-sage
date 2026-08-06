package com.mediasage.feature.briefing

/**
 * Arms or disarms the local "evening tone" notification depending on whether the Briefing screen
 * is currently on-screen. Scheduling goes through each platform's own OS-level timer
 * (`AlarmManager` / `UNUserNotificationCenter`) rather than an in-process delay, since the
 * notification must still fire even if the app process isn't running when 5pm arrives — only the
 * midnight boundary is ever silent, this scheduler never arms for it.
 */
interface BriefingNotificationScheduler {
    fun onBriefingVisible()
    fun onBriefingHidden()
}
