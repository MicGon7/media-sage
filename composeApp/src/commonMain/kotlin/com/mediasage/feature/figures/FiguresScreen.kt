package com.mediasage.feature.figures

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediasage.ui.FigurePlaceholder
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun FiguresScreen(
    state: FiguresContract.UiState,
    onIntent: (FiguresContract.Intent) -> Unit
) {
    when (state) {
        is FiguresContract.UiState.Loading -> LoadingState()
        is FiguresContract.UiState.Error -> ErrorState(
            message = state.message,
            onRetry = { onIntent(FiguresContract.Intent.LoadFigures) }
        )
        is FiguresContract.UiState.Success -> VoicesList(
            figures = state.figures
        )
    }
}

@Composable
private fun VoicesHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(Res.string.title_voices),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.voices_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )
    }
}

@Composable
private fun VoicesList(figures: List<FigureItem>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { VoicesHeader() }

        items(figures, key = { it.id }) { figure ->
            VoiceCard(figure = figure)
        }
    }
}

@Composable
private fun VoiceCard(figure: FigureItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            FigurePlaceholder(
                name = figure.name,
                size = 64.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = figure.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (figure.role.isNotBlank()) {
                    Text(
                        text = figure.role,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (figure.lifespan.isNotBlank()) {
                    Text(
                        text = figure.lifespan,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (figure.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = figure.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.voices_retry))
        }
    }
}
