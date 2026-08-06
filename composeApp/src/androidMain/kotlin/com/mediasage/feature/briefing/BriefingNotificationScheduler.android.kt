package com.mediasage.feature.briefing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

internal const val EVENING_TONE_NOTIFICATION_ID = 1001
private const val ALARM_WINDOW_MILLIS = 10 * 60 * 1000L

/**
 * Arms a one-shot inexact `AlarmManager.setWindow` alarm for the next 5pm boundary whenever the
 * Briefing screen goes out of view, and tears it down (cancelling the alarm and any notification
 * already showing) whenever the screen comes back — so a user actively viewing the live tone
 * update at 5pm never sees a redundant notification. `setWindow` deliberately avoids the exact-
 * alarm APIs, which would require the separate "Alarms & reminders" permission.
 */
class AndroidBriefingNotificationScheduler(
    private val context: Context,
) : BriefingNotificationScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun onBriefingVisible() {
        alarmManager.cancel(alarmPendingIntent())
        NotificationManagerCompat.from(context).cancel(EVENING_TONE_NOTIFICATION_ID)
    }

    override fun onBriefingHidden() {
        val triggerAtMillis = System.currentTimeMillis() + millisUntilNext5pm()
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            ALARM_WINDOW_MILLIS,
            alarmPendingIntent(),
        )
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(context, BriefingToneNotificationReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            EVENING_TONE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
