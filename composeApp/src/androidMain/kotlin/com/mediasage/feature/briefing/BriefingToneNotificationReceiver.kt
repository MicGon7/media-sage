package com.mediasage.feature.briefing

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mediasage.MainActivity
import com.mediasage.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.notification_evening_tone_text
import mediasage.composeapp.generated.resources.notification_evening_tone_title
import org.jetbrains.compose.resources.getString

private const val CHANNEL_ID = "briefing_tone"
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Fires when the [AndroidBriefingNotificationScheduler]-armed 5pm alarm goes off. Runs
 * independently of the app process, so it re-checks the runtime notification permission itself
 * rather than trusting that it's still granted — declining or revoking it must not crash here.
 */
class BriefingToneNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                showNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun showNotification(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_briefing_name))
                .build()
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            EVENING_TONE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(Res.string.notification_evening_tone_title))
            .setContentText(getString(Res.string.notification_evening_tone_text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(EVENING_TONE_NOTIFICATION_ID, notification)
    }
}
