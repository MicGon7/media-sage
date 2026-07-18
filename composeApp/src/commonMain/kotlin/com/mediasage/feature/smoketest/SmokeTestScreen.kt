package com.mediasage.feature.smoketest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A deliberately minimal, self-contained screen that exists only to exercise the
 * headless UI render loop (MS-581). It is the visual analog of the
 * `smoke-test-version` counter in `:agentruntime`'s `SmokeTest.kt`: a dead-simple,
 * deterministic edit target that a UI pipeline smoke test can tweak, render to a
 * PNG, and attach to the PR.
 *
 * It is intentionally NOT wired into navigation or DI — it takes no ViewModel and
 * no Koin. Its only job is to give the worker a trivial composable to render and
 * self-critique.
 */
@Composable
fun SmokeTestScreen(modifier: Modifier = Modifier) {
    // smoke-test-ui-version: 4
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "The Media Sage",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "UI render smoke test",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "v4",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
