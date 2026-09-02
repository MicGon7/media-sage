package com.mediasage.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.theme.MediaSageTheme

/**
 * A confirm/dismiss [AlertDialog] with bold title/confirm text for emphasis. Colors come from
 * [MaterialTheme.colorScheme], so it adapts to the active app theme and dark/light mode the same
 * way other Material3 components do. `containerColor` is pinned to [ColorScheme.surface] rather
 * than left at the [AlertDialog] default ([ColorScheme.surfaceContainerHigh]): none of this app's
 * three themes (`Theme.kt`) set that newer M3 surface-container role, so it falls back to M3's
 * baseline purple instead of the app's actual palette.
 */
@Composable
fun MediaSageConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        },
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageConfirmDialogPreview() {
    MediaSageTheme {
        MediaSageConfirmDialog(
            title = "Discard changes?",
            message = "Your reflection note hasn't been saved yet. Discard it?",
            confirmLabel = "Discard",
            dismissLabel = "Keep Editing",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

// endregion
