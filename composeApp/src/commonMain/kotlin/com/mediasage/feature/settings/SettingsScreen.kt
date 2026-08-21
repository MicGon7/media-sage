package com.mediasage.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.AppTheme
import com.mediasage.theme.MediaSageTheme
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.nav_back
import mediasage.composeapp.generated.resources.settings_edit_profile
import mediasage.composeapp.generated.resources.settings_privacy_policy
import mediasage.composeapp.generated.resources.settings_section_account
import mediasage.composeapp.generated.resources.settings_section_appearance
import mediasage.composeapp.generated.resources.settings_section_support
import mediasage.composeapp.generated.resources.settings_send_feedback
import mediasage.composeapp.generated.resources.settings_developer_credit
import mediasage.composeapp.generated.resources.settings_sign_out
import mediasage.composeapp.generated.resources.settings_terms_of_service
import mediasage.composeapp.generated.resources.settings_text_size_label
import mediasage.composeapp.generated.resources.settings_theme_label
import mediasage.composeapp.generated.resources.settings_dark_mode_label
import mediasage.composeapp.generated.resources.settings_version_label
import mediasage.composeapp.generated.resources.title_settings
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val TEXT_SCALE_MIN_PERCENT = 85
private const val TEXT_SCALE_MAX_PERCENT = 150
private const val TEXT_SCALE_DEFAULT_PERCENT = 100
private const val TEXT_SCALE_STEP_PERCENT = 5
private const val TEXT_SCALE_STEPS =
    (TEXT_SCALE_MAX_PERCENT - TEXT_SCALE_MIN_PERCENT) / TEXT_SCALE_STEP_PERCENT - 1

@Composable
fun SettingsScreen(
    state: SettingsContract.UiState,
    onIntent: (SettingsContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val ready = state as? SettingsContract.UiState.Ready

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.nav_back),
                    )
                }
                Text(
                    text = stringResource(Res.string.title_settings),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // ── Appearance ────────────────────────────────────────────────
                SettingsSectionHeader(stringResource(Res.string.settings_section_appearance))

                SettingsRow(label = stringResource(Res.string.settings_theme_label)) {
                    if (ready != null) {
                        SingleChoiceSegmentedButtonRow {
                            AppTheme.entries.forEachIndexed { index, theme ->
                                SegmentedButton(
                                    selected = ready.appTheme == theme,
                                    onClick = { onIntent(SettingsContract.Intent.SetAppTheme(theme)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                                    label = { Text(theme.label, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }

                SettingsRow(label = stringResource(Res.string.settings_dark_mode_label)) {
                    if (ready != null) {
                        Switch(
                            checked = ready.darkMode,
                            onCheckedChange = { onIntent(SettingsContract.Intent.ToggleDarkMode(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                }

                TextScaleRow(
                    percent = ready?.textScalePercent ?: TEXT_SCALE_DEFAULT_PERCENT,
                    onPercentChange = { onIntent(SettingsContract.Intent.SetTextScalePercent(it)) },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Account ───────────────────────────────────────────────────
                SettingsSectionHeader(stringResource(Res.string.settings_section_account))

                SettingsRow(label = stringResource(Res.string.settings_edit_profile)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!ready?.displayName.isNullOrEmpty()) {
                            Text(
                                text = ready.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsRow(label = stringResource(Res.string.settings_version_label)) {
                    Text(
                        text = ready?.appVersion ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Support ───────────────────────────────────────────────────
                SettingsSectionHeader(stringResource(Res.string.settings_section_support))

                SettingsRow(label = stringResource(Res.string.settings_privacy_policy)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsRow(label = stringResource(Res.string.settings_terms_of_service)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsRow(label = stringResource(Res.string.settings_send_feedback)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Sign Out ──────────────────────────────────────────────────────
            OutlinedButton(
                onClick = { onIntent(SettingsContract.Intent.SignOut) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = stringResource(Res.string.settings_sign_out),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = stringResource(Res.string.settings_developer_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SettingsRow(
    label: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun TextScaleRow(
    percent: Int,
    onPercentChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.settings_text_size_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.roundToInt()) },
            valueRange = TEXT_SCALE_MIN_PERCENT.toFloat()..TEXT_SCALE_MAX_PERCENT.toFloat(),
            steps = TEXT_SCALE_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MediaSageTheme {
        SettingsScreen(
            state = SettingsContract.UiState.Ready(),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenDarkPreview() {
    MediaSageTheme(darkTheme = true) {
        SettingsScreen(
            state = SettingsContract.UiState.Ready(darkMode = true),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenModernPreview() {
    MediaSageTheme(theme = AppTheme.MODERN) {
        SettingsScreen(
            state = SettingsContract.UiState.Ready(appTheme = AppTheme.MODERN),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenWarmPreview() {
    MediaSageTheme(theme = AppTheme.WARM) {
        SettingsScreen(
            state = SettingsContract.UiState.Ready(appTheme = AppTheme.WARM),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLargeTextPreview() {
    MediaSageTheme(textScalePercent = 130) {
        SettingsScreen(
            state = SettingsContract.UiState.Ready(textScalePercent = 130),
            onIntent = {},
        )
    }
}
