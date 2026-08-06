package com.mediasage.feature.briefing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.notification_evening_tone_text
import mediasage.composeapp.generated.resources.notification_evening_tone_title
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

private const val NOTIFICATION_IDENTIFIER = "briefing_evening_tone"

/**
 * Arms a one-shot `UNCalendarNotificationTrigger` for the next 5pm local time whenever the
 * Briefing screen goes out of view, and removes it (pending and already-delivered) whenever the
 * screen comes back — so a user actively viewing the live tone update at 5pm never sees a
 * redundant notification, and returning to the Briefing re-arms it for the next visit. This
 * matches Android's one-shot-per-visit `AlarmManager` semantics. iOS resolves "next occurrence of
 * this wall-clock time" itself, unlike Android's AlarmManager.
 */
class IosBriefingNotificationScheduler : BriefingNotificationScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBriefingVisible() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_IDENTIFIER))
    }

    override fun onBriefingHidden() {
        scope.launch {
            val content = UNMutableNotificationContent().apply {
                setTitle(getString(Res.string.notification_evening_tone_title))
                setBody(getString(Res.string.notification_evening_tone_text))
            }
            val dateComponents = NSDateComponents().apply {
                hour = TONE_BOUNDARY_HOUR.toLong()
                minute = 0
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = dateComponents,
                repeats = false,
            )
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = NOTIFICATION_IDENTIFIER,
                content = content,
                trigger = trigger,
            )
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
        }
    }
}
