package com.mediasage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.Navy
import com.mediasage.theme.SlateOnPaper
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.comic_paper
import mediasage.composeapp.generated.resources.reflect_paper_light
import org.jetbrains.compose.resources.painterResource

/**
 * A confirm/dismiss dialog painted on the same comic-paper texture and serif type as
 * [ReflectionSheet], instead of a stock [androidx.compose.material3.AlertDialog] — that default
 * renders with Material3's baseline tonal-elevation surface, which reads as generic M3 chrome
 * against the rest of the app's newspaper styling. Pure `commonMain` Compose with no
 * `expect`/`actual`, so it renders identically on Android and iOS.
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
    val paperImage = if (MediaSageTheme.isDark) Res.drawable.comic_paper else Res.drawable.reflect_paper_light
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .paint(painter = painterResource(paperImage), contentScale = ContentScale.FillBounds)
                .padding(24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Navy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateOnPaper,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissLabel, color = SlateOnPaper)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text(text = confirmLabel, color = Navy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
