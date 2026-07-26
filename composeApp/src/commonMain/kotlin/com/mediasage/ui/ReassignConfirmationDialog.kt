package com.mediasage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reassign_dialog_cancel
import mediasage.composeapp.generated.resources.reassign_dialog_confirm
import mediasage.composeapp.generated.resources.reassign_dialog_hint
import mediasage.composeapp.generated.resources.reassign_dialog_message
import mediasage.composeapp.generated.resources.reassign_dialog_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReassignConfirmationDialog(
    currentFigureName: String,
    newFigureName: String,
    nextWeekdayLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showSchedulerHint: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(Res.string.reassign_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        Res.string.reassign_dialog_message,
                        currentFigureName,
                        newFigureName,
                        nextWeekdayLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (showSchedulerHint) {
                    Text(
                        text = stringResource(Res.string.reassign_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.reassign_dialog_confirm),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.reassign_dialog_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
