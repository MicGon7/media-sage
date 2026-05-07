package com.mediasage.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.LocalIsDebugBuild
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.MediaSageButton
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.dev_dark_mode_label
import mediasage.composeapp.generated.resources.dev_section_header
import mediasage.composeapp.generated.resources.you_activity_subheader
import mediasage.composeapp.generated.resources.you_greeting
import mediasage.composeapp.generated.resources.you_nav_history
import mediasage.composeapp.generated.resources.you_nav_saved
import mediasage.composeapp.generated.resources.you_settings_icon_description
import org.jetbrains.compose.resources.stringResource

@Composable
fun YouScreen(
    state: YouContract.UiState,
    onIntent: (YouContract.Intent) -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val isDebugBuild = LocalIsDebugBuild.current
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.you_greeting),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.you_activity_subheader),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(Res.string.you_settings_icon_description)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaSageButton(
                    icon = Icons.Outlined.Bookmark,
                    label = stringResource(Res.string.you_nav_saved),
                    onClick = onNavigateToBookmarks,
                    modifier = Modifier.weight(1f)
                )
                MediaSageButton(
                    icon = Icons.Outlined.History,
                    label = stringResource(Res.string.you_nav_history),
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isDebugBuild && state is YouContract.UiState.Ready) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(Res.string.dev_section_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.dev_dark_mode_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.darkMode,
                        onCheckedChange = { onIntent(YouContract.Intent.ToggleDarkMode(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        )
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun YouScreenPreview() {
    MediaSageTheme {
        YouScreen(
            state = YouContract.UiState.Ready(),
            onIntent = {}
        )
    }
}
