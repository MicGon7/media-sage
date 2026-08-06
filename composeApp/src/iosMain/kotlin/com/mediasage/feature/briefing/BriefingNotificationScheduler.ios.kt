package com.mediasage.feature.briefing

import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

private const val NOTIFICATION_IDENTIFIER = "briefing_evening_tone"

/**
 * Arms a daily-repeating `UNCalendarNotificationTrigger` for 5pm local time whenever the Briefing
 * screen goes out of view, and removes it (pending and already-delivered) whenever the screen
 * comes back — so a user actively viewing the live tone update at 5pm never sees a redundant
 * notification. iOS resolves "next occurrence of this wall-clock time" itself, unlike Android's
 * AlarmManager.
 */
class IosBriefingNotificationScheduler : BriefingNotificationScheduler {

    override fun onBriefingVisible() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_IDENTIFIER))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_IDENTIFIER))
    }

    override fun onBriefingHidden() {
        val content = UNMutableNotificationContent().apply {
            title = "Your evening reflection is ready"
            body = "Tonight's Briefing has a new reflection waiting for you."
        }
        val dateComponents = NSDateComponents().apply {
            hour = TONE_BOUNDARY_HOUR.toLong()
            minute = 0
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = dateComponents,
            repeats = true,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = NOTIFICATION_IDENTIFIER,
            content = content,
            trigger = trigger,
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }
}
