package com.mediasage.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun ThemePreviewCard() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Text(
            text = "Peace Amid the Storm",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Do not be anxious about anything, but in every situation, by prayer and petition, present your requests to God.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MediaSageTheme.colors.ruleLine)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Read More")
        }
    }
}

@Preview(name = "Classic Light", showBackground = true)
@Composable
private fun ClassicLightPreview() {
    MediaSageTheme(theme = AppTheme.CLASSIC, darkTheme = false) {
        ThemePreviewCard()
    }
}

@Preview(name = "Classic Dark", showBackground = true, backgroundColor = 0xFF1C1A14)
@Composable
private fun ClassicDarkPreview() {
    MediaSageTheme(theme = AppTheme.CLASSIC, darkTheme = true) {
        ThemePreviewCard()
    }
}

@Preview(name = "Modern Light", showBackground = true)
@Composable
private fun ModernLightPreview() {
    MediaSageTheme(theme = AppTheme.MODERN, darkTheme = false) {
        ThemePreviewCard()
    }
}

@Preview(name = "Modern Dark", showBackground = true, backgroundColor = 0xFF1A1E2E)
@Composable
private fun ModernDarkPreview() {
    MediaSageTheme(theme = AppTheme.MODERN, darkTheme = true) {
        ThemePreviewCard()
    }
}

@Preview(name = "Warm Light", showBackground = true, backgroundColor = 0xFFF8F9FF)
@Composable
private fun WarmLightPreview() {
    MediaSageTheme(theme = AppTheme.WARM, darkTheme = false) {
        ThemePreviewCard()
    }
}

@Preview(name = "Warm Dark", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun WarmDarkPreview() {
    MediaSageTheme(theme = AppTheme.WARM, darkTheme = true) {
        ThemePreviewCard()
    }
}
