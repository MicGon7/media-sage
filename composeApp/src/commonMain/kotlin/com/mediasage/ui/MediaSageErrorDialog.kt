package com.mediasage.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * Blocking error state (e.g. first launch with no cached data) — no dismiss button, since
 * there is nothing behind the dialog to fall back to. Retry is the only way forward.
 */
@Composable
fun MediaSageErrorDialog(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onRetry,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(
                    text = retryLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
