package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
 * the note renders as read-only text and no save/discard affordance is shown. [noteText] is
 * `null` while the saved note is still loading (the sheet opens on [challenge] alone, without
 * waiting on it) — the body shows a loading indicator in place of the field until it resolves.
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
    noteText: String?,
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
        MediaSageConfirmDialog(
            title = stringResource(Res.string.reflect_discard_title),
            message = stringResource(Res.string.reflect_discard_message),
            confirmLabel = stringResource(Res.string.reflect_discard_confirm),
            dismissLabel = stringResource(Res.string.reflect_discard_cancel),
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun ReflectionSheetContent(
    challenge: String,
    noteText: String?,
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
    val showSavedLabel = !hasUnsavedChanges && noteText?.isNotBlank() == true

    val scrollState = rememberScrollState()
    var isFieldFocused by remember { mutableStateOf(false) }
    // The field displays its own locally-owned text, not `noteText` directly. `noteText` round-trips
    // through the ViewModel's StateFlow on every keystroke (onNoteChange copies the whole
    // UiState.Success tree, which recomposes back down as a new noteText) — on iOS, Compose
    // Multiplatform's software-keyboard input reconciles against the field's `value` each
    // recomposition, and driving that off a StateFlow round trip instead of local state made fast
    // typing outrun recomposition and skip characters. Seeding once, when the note finishes loading
    // (null -> non-null), keeps the field's displayed text purely local while still forwarding every
    // change to the ViewModel for save/unsaved-changes tracking.
    var fieldText by remember { mutableStateOf(noteText.orEmpty()) }
    var hasSeededFieldText by remember { mutableStateOf(false) }
    LaunchedEffect(noteText) {
        if (noteText != null && !hasSeededFieldText) {
            fieldText = noteText
            hasSeededFieldText = true
        }
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // The note field grows with its content instead of scrolling internally, so once it's taller
    // than the visible area the newest typed line — and the cursor on it — can end up behind the
    // keyboard. BringIntoViewRequester asks the framework to scroll just enough to reveal the
    // field's current bounds, rather than a manually computed scrollState.animateScrollTo(maxValue)
    // on every keystroke — that approach raced the field's own height/cursor recalculation and was
    // the source of the visible cursor lagging behind newly typed characters once the note wrapped
    // past one line. Only while the field is actually focused, not on the initial load of an
    // existing note.
    LaunchedEffect(noteText, isFieldFocused) {
        if (isFieldFocused) bringIntoViewRequester.bringIntoView()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Wraps content height by default, which is shorter than the sheet's full drag
            // range — with the transparent ModalBottomSheet container, dragging past that
            // point (e.g. to Expanded with the keyboard up, shrinking the visible content
            // area further) reveals the screen behind through the un-painted gap below this
            // Column. fillMaxHeight keeps the painted background spanning the whole sheet.
            .fillMaxHeight()
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
            style = MaterialTheme.typography.bodyMedium,
            color = mutedColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = challenge,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color = bodyColor,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            if (noteText == null) {
                NoteLoadingIndicator(color = titleColor)
            } else if (editable) {
                OutlinedTextField(
                    value = fieldText,
                    onValueChange = {
                        fieldText = it
                        onNoteChange(it)
                    },
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
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged {
                            isFieldFocused = it.isFocused
                            if (it.isFocused) onFieldFocused()
                        },
                )
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
            } else if (noteText.isNotBlank()) {
                ReflectionAnswerText(noteText = noteText, bodyColor = bodyColor, dividerColor = borderColor)
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

/** Shown in place of the note field/answer text while the saved note is still loading. */
@Composable
private fun NoteLoadingIndicator(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = color, modifier = Modifier.size(28.dp))
    }
}

/**
 * The read-only presentation of a past reflection's saved note — plain text, no text-field
 * chrome, set off from the AI-generated [challenge] above it by a divider and a bolder weight so
 * the human-written answer visually stands out.
 */
@Composable
private fun ReflectionAnswerText(noteText: String, bodyColor: Color, dividerColor: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        HorizontalDivider(color = dividerColor, thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = noteText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = bodyColor,
        )
    }
}
