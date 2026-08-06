package com.mediasage.feature.briefing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun RequestNotificationPermissionEffect() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }
}
