package com.mediasage.feature.briefing

import androidx.compose.runtime.Composable

/**
 * Prompts for the platform's standard notification permission the first time it runs, so the
 * evening-tone notification can later be delivered. A no-op if permission was already granted or
 * already declined — each platform's own permission API tracks that, this effect just asks once.
 */
@Composable
expect fun RequestNotificationPermissionEffect()
