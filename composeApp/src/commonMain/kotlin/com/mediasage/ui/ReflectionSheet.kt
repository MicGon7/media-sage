package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mediasage.theme.CardBorder
import com.mediasage.theme.Ink
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.Navy
import com.mediasage.theme.SlateOnPaper
import kotlinx.coroutines.launch
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.comic_paper
import mediasage.composeapp.generated.resources.reflect_close_action
import mediasage.composeapp.generated.resources.reflect_discard_cancel
import mediasage.composeapp.generated.resources.reflect_discard_confirm
import mediasage.composeapp.generated.resources.reflect_discard_message
import mediasage.composeapp.generated.resources.reflect_discard_title
import mediasage.composeapp.generated.resources.reflect_note_hint
import mediasage.composeapp.generated.resources.reflect_paper_light
import mediasage.composeapp.generated.resources.reflect_sheet_subtitle
import mediasage.composeapp.generated.resources.reflect_save_action
import mediasage.composeapp.generated.resources.reflect_saved_confirmation
import mediasage.composeapp.generated.resources.reflect_sheet_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Reflect chip's bottom sheet — the reflection challenge question with a note field beneath
 * it. Opens partially expanded with the briefing still visible behind it, draggable to full
 * height; [Modifier.imePadding] on the content keeps the keyboard from covering the field at
 * either height. When [editable] is false (a past briefing whose tone slot is no longer active),
 * the note renders as read-only text and no save/discard affordance is shown.
 *
 * [SheetState]'s `confirmValueChange` — not `onDismissRequest` — is what gates a *drag* to
 * hidden: a swipe-to-dismiss settles the sheet to [SheetValue.Hidden] before `onDismissRequest`
 * ever fires, so vetoing there is the only way to keep the sheet visually open underneath the
 * discard dialog. `onDismissRequest` (scrim tap, back press) never changes `sheetState` itself,
 * so it can safely decide to show the dialog without the sheet having moved.
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
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val hasUnsavedChangesState = rememberUpdatedState(hasUnsavedChanges)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { target ->
            val shouldVeto = target == SheetValue.Hidden && hasUnsavedChangesState.value
            if (shouldVeto) showDiscardDialog = true
            !shouldVeto
        },
    )
    val scope = rememberCoroutineScope()
    val requestDismiss = { if (hasUnsavedChanges) showDiscardDialog = true else onDismiss() }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = null,
    ) {
        ReflectionSheetContent(
            challenge = challenge,
            noteText = noteText,
            editable = editable,
            hasUnsavedChanges = hasUnsavedChanges,
            onNoteChange = onNoteChange,
            onSave = onSave,
            onCloseClick = requestDismiss,
            onFieldFocused = { scope.launch { sheetState.expand() } },
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
    hasUnsavedChanges: Boolean,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseClick: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    // The paper is always a light surface — a tan comic-paper texture in dark mode, white in
    // light mode — so its ink stays the same fixed dark-on-paper palette in both, matching
    // LoginScreen's formOnPaper convention. Only the painted texture itself switches on theme.
    val titleColor = Navy
    val bodyColor = Ink
    val mutedColor = SlateOnPaper
    val borderColor = CardBorder
    val paperImage = if (MediaSageTheme.isDark) Res.drawable.comic_paper else Res.drawable.reflect_paper_light
    val background = Modifier
        .shadow(elevation = 0.dp, shape = MaterialTheme.shapes.medium, clip = false)
        .paint(painter = painterResource(paperImage), contentScale = ContentScale.FillBounds)

    // "Saved" reflects a real comparison against the last-saved text, not a one-shot flag from
    // the save action — so editing away from and then back to the saved value (e.g. deleting and
    // retyping the last character) correctly reads as saved again, with no separate state to sync.
    val showSavedLabel = !hasUnsavedChanges && noteText.isNotBlank()

    val scrollState = rememberScrollState()
    var isFieldFocused by remember { mutableStateOf(false) }
    // The note field grows with its content instead of scrolling internally, so once it's taller
    // than the visible area the newest typed line can end up behind the keyboard — only while the
    // field is actually focused, not on the initial load of an existing note, keep the bottom (the
    // cursor's end) in view as each keystroke grows it further.
    LaunchedEffect(noteText) {
        if (isFieldFocused) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .then(background)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ReflectionSheetHandle(color = mutedColor)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(
                text = stringResource(Res.string.reflect_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
            IconButton(onClick = onCloseClick, modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.reflect_close_action),
                    tint = mutedColor,
                )
            }
        }
        Text(
            text = stringResource(Res.string.reflect_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = challenge,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = bodyColor,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                readOnly = !editable,
                placeholder = { Text(stringResource(Res.string.reflect_note_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = bodyColor,
                    unfocusedTextColor = bodyColor,
                    disabledTextColor = bodyColor,
                    focusedBorderColor = titleColor,
                    unfocusedBorderColor = borderColor,
                    focusedLabelColor = titleColor,
                    unfocusedLabelColor = mutedColor,
                    focusedPlaceholderColor = mutedColor,
                    unfocusedPlaceholderColor = mutedColor,
                    cursorColor = titleColor,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                    .onFocusChanged {
                        isFieldFocused = it.isFocused
                        if (it.isFocused) onFieldFocused()
                    },
            )
            if (editable) {
                MediaSageSurface(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                    bordered = true,
                    shadowElevation = 2.dp,
                    enabled = hasUnsavedChanges,
                ) { contentColor ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(
                                if (showSavedLabel) Res.string.reflect_saved_confirmation else Res.string.reflect_save_action
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Replaces [ModalBottomSheet]'s default drag handle, which is drawn above the sheet content and
 * left the paper texture looking disconnected from it. Rendered as the first child inside the
 * painted paper [Column] instead, so the handle sits on the same continuous surface.
 */
@Composable
private fun ReflectionSheetHandle(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
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
