package com.mediasage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reflect_discard_cancel
import mediasage.composeapp.generated.resources.reflect_discard_confirm
import mediasage.composeapp.generated.resources.reflect_discard_message
import mediasage.composeapp.generated.resources.reflect_discard_title
import mediasage.composeapp.generated.resources.reflect_note_hint
import mediasage.composeapp.generated.resources.reflect_save_action
import mediasage.composeapp.generated.resources.reflect_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Reflect chip's bottom sheet — the reflection challenge question with a note field beneath
 * it. Opens partially expanded with the briefing still visible behind it, draggable to full
 * height; [Modifier.imePadding] on the content keeps the keyboard from covering the field at
 * either height. When [editable] is false (a past briefing whose tone slot is no longer active),
 * the note renders as read-only text and no save/discard affordance is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionSheet(
    challenge: String,
    noteText: String,
    editable: Boolean,
    hasUnsavedChanges: Boolean,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestDismiss = { if (hasUnsavedChanges) showDiscardDialog = true else onDismiss() }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Color.Transparent,
    ) {
        ReflectionSheetContent(
            challenge = challenge,
            noteText = noteText,
            editable = editable,
            onNoteChange = onNoteChange,
            onSave = onSave,
        )
    }

    if (showDiscardDialog) {
        ReflectDiscardDialog(
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onCancel = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun ReflectionSheetContent(
    challenge: String,
    noteText: String,
    editable: Boolean,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .paperSurface(elevation = 0.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .imePadding(),
    ) {
        Text(
            text = stringResource(Res.string.reflect_sheet_title),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = challenge,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                readOnly = !editable,
                placeholder = { Text(stringResource(Res.string.reflect_note_hint)) },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            if (editable) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                ) {
                    Text(stringResource(Res.string.reflect_save_action))
                }
            }
        }
    }
}

@Composable
private fun ReflectDiscardDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.reflect_discard_title)) },
        text = { Text(stringResource(Res.string.reflect_discard_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.reflect_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.reflect_discard_cancel))
            }
        },
    )
}
