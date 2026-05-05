package com.mediasage.feature.figures

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mediasage.theme.MediaSageTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.ui.FigurePlaceholder
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.title_voices
import mediasage.composeapp.generated.resources.voices_empty_state
import mediasage.composeapp.generated.resources.voices_subtitle
import org.jetbrains.compose.resources.stringResource

@Composable
fun FiguresScreen(
    state: FiguresContract.UiState,
    onIntent: (FiguresContract.Intent) -> Unit,
    onNavigateToFigureDetail: (figureId: Long) -> Unit = {}
) {
    when (state) {
        is FiguresContract.UiState.Loading -> LoadingState()
        is FiguresContract.UiState.Success -> VoicesList(
            figures = state.figures,
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(FiguresContract.Intent.Refresh) },
            onFigureClick = { id ->
                onIntent(FiguresContract.Intent.FigureClicked(id))
                onNavigateToFigureDetail(id)
            }
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
private fun VoicesList(
    figures: List<VoiceFigureItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFigureClick: (Long) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                    onRefresh = onRefresh
                )
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { VoicesHeader() }

                if (figures.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(figures, key = { it.id }) { figure ->
                        VoiceCard(figure = figure, onClick = { onFigureClick(figure.id) })
                    }
                }
            }

            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun VoiceCard(figure: VoiceFigureItem, onClick: () -> Unit) {
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (figure.imageUrl != null) {
                    AsyncImage(
                        model = figure.imageUrl,
                        contentDescription = figure.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                } else {
                    FigurePlaceholder(name = figure.name, size = 64.dp)
                }

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
                }
            }
        }

        if (figure.quoteCount > 0) {
            Text(
                text = "${figure.quoteCount} Qs",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = 2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (figure.isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = (-8).dp)
                    .size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.voices_empty_state),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

// region Previews

private class FiguresStateProvider : PreviewParameterProvider<FiguresContract.UiState> {
    override val values = sequenceOf(
        FiguresContract.UiState.Loading,
        FiguresContract.UiState.Success(figures = emptyList()),
        FiguresContract.UiState.Success(
            figures = listOf(
                VoiceFigureItem(id = 1L, name = "C.S. Lewis", role = "Author & Apologist", imageUrl = null, quoteCount = 3, isPinned = true),
                VoiceFigureItem(id = 2L, name = "Dietrich Bonhoeffer", role = "Theologian & Martyr", imageUrl = null, quoteCount = 1),
                VoiceFigureItem(id = 3L, name = "Martin Luther King Jr.", role = "Pastor & Civil Rights Leader", imageUrl = null, quoteCount = 0),
            ),
            isRefreshing = false
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun FiguresScreenPreview(
    @PreviewParameter(FiguresStateProvider::class) state: FiguresContract.UiState
) {
    MediaSageTheme {
        FiguresScreen(state = state, onIntent = {})
    }
}

// endregion
