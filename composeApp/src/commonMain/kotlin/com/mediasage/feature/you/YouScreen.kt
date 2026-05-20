package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.AppTheme
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.MediaSageButton
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_greeting_afternoon
import mediasage.composeapp.generated.resources.you_greeting_evening
import mediasage.composeapp.generated.resources.you_greeting_morning
import mediasage.composeapp.generated.resources.you_nav_history
import mediasage.composeapp.generated.resources.you_nav_saved
import mediasage.composeapp.generated.resources.you_screen_title
import mediasage.composeapp.generated.resources.you_settings_icon_description
import org.jetbrains.compose.resources.stringResource

@Composable
fun YouScreen(
    state: YouContract.UiState,
    onIntent: (YouContract.Intent) -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.you_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(Res.string.you_settings_icon_description),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                if (state is YouContract.UiState.Ready) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val greetingWord = stringResource(
                        when (state.greeting) {
                            YouContract.Greeting.MORNING -> Res.string.you_greeting_morning
                            YouContract.Greeting.AFTERNOON -> Res.string.you_greeting_afternoon
                            YouContract.Greeting.EVENING -> Res.string.you_greeting_evening
                        }
                    )
                    val greetingText = if (state.displayName.isNotBlank()) {
                        "$greetingWord, ${state.displayName}"
                    } else {
                        greetingWord
                    }
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MediaSageButton(
                    icon = Icons.Outlined.Bookmark,
                    label = stringResource(Res.string.you_nav_saved),
                    onClick = onNavigateToBookmarks,
                    modifier = Modifier.weight(1f),
                )
                MediaSageButton(
                    icon = Icons.Outlined.History,
                    label = stringResource(Res.string.you_nav_history),
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun YouScreenPreview() {
    MediaSageTheme {
        YouScreen(
            state = YouContract.UiState.Ready(displayName = "reader@example.com"),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun YouScreenDarkPreview() {
    MediaSageTheme(darkTheme = true) {
        YouScreen(
            state = YouContract.UiState.Ready(displayName = "reader@example.com"),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun YouScreenModernPreview() {
    MediaSageTheme(theme = AppTheme.MODERN) {
        YouScreen(
            state = YouContract.UiState.Ready(displayName = "reader@example.com"),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun YouScreenWarmPreview() {
    MediaSageTheme(theme = AppTheme.WARM) {
        YouScreen(
            state = YouContract.UiState.Ready(displayName = "reader@example.com"),
            onIntent = {},
        )
    }
}
