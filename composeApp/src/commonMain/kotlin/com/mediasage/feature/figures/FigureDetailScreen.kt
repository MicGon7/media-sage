package com.mediasage.feature.figures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import com.mediasage.ui.MediaSageTabRow
import com.mediasage.ui.QuoteCard
import com.mediasage.ui.ReassignConfirmationDialog
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.figure_detail_biography
import mediasage.composeapp.generated.resources.figure_detail_no_biography
import mediasage.composeapp.generated.resources.figure_detail_no_quotes
import mediasage.composeapp.generated.resources.figure_detail_pin_to_home
import mediasage.composeapp.generated.resources.figure_detail_pinned_to_home
import mediasage.composeapp.generated.resources.figure_detail_quotes_sheet_title
import mediasage.composeapp.generated.resources.figure_detail_tab_quotes
import mediasage.composeapp.generated.resources.figure_detail_tab_writings
import mediasage.composeapp.generated.resources.figure_detail_writings_placeholder
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun FigureDetailScreen(
    state: FigureDetailContract.UiState,
    onIntent: (FigureDetailContract.Intent) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val success = state as? FigureDetailContract.UiState.Success
    val figureName = success?.figureName

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                AnimatedVisibility(visible = figureName != null, enter = fadeIn()) {
                    Text(
                        text = figureName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            when (state) {
                is FigureDetailContract.UiState.Loading -> LoadingIndicator()
                is FigureDetailContract.UiState.Error -> ErrorMessage(state.message)
                is FigureDetailContract.UiState.Success -> {
                    FigureDetailContent(
                        state = state,
                        onPinToggle = { onIntent(FigureDetailContract.Intent.PinToHome) },
                        onPinQuote = { onIntent(FigureDetailContract.Intent.PinQuote(it)) },
                    )

                    state.pendingReassignment?.let { pending ->
                        ReassignConfirmationDialog(
                            currentFigureName = pending.currentFigureName,
                            newFigureName = pending.newFigureName,
                            nextWeekdayLabel = pending.nextWeekdayLabel,
                            onConfirm = { onIntent(FigureDetailContract.Intent.ConfirmReassignment) },
                            onDismiss = { onIntent(FigureDetailContract.Intent.CancelReassignment) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

private enum class FigureDetailTab(val labelRes: StringResource) {
    BIOGRAPHY(Res.string.figure_detail_biography),
    QUOTES(Res.string.figure_detail_tab_quotes),
    WRITINGS(Res.string.figure_detail_tab_writings),
}

@Composable
private fun FigureDetailContent(
    state: FigureDetailContract.UiState.Success,
    onPinToggle: () -> Unit,
    onPinQuote: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(FigureDetailTab.BIOGRAPHY) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                FigureDetailTab.BIOGRAPHY -> BiographyTabContent(state = state, onPinToggle = onPinToggle)
                FigureDetailTab.QUOTES -> QuotesTabContent(
                    state = state,
                    onPinToggle = onPinToggle,
                    onPinQuote = onPinQuote,
                )
                FigureDetailTab.WRITINGS -> WritingsTabContent(state = state, onPinToggle = onPinToggle)
            }
        }

        MediaSageTabRow(
            selectedIndex = selectedTab.ordinal,
            tabLabels = FigureDetailTab.entries.map { stringResource(it.labelRes) },
            onTabSelected = { index -> selectedTab = FigureDetailTab.entries[index] },
        )
    }
}

@Composable
private fun FigureHero(state: FigureDetailContract.UiState.Success, onPinToggle: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        if (state.figureImageUrl != null) {
            AsyncImage(
                model = state.figureImageUrl,
                contentDescription = state.figureName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                FigurePlaceholder(name = state.figureName, size = 120.dp)
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.figureName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.figureRole.isNotBlank()) {
                    Text(
                        text = state.figureRole,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onPinToggle) {
                Icon(
                    imageVector = if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(
                        if (state.isPinned) Res.string.figure_detail_pinned_to_home
                        else Res.string.figure_detail_pin_to_home
                    ),
                    tint = if (state.isPinned) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
    }
}

@Composable
private fun BiographyTabContent(state: FigureDetailContract.UiState.Success, onPinToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        FigureHero(state = state, onPinToggle = onPinToggle)

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (state.bio.isNullOrBlank()) {
                Text(
                    text = stringResource(Res.string.figure_detail_no_biography),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(Res.string.figure_detail_biography),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun QuotesTabContent(
    state: FigureDetailContract.UiState.Success,
    onPinToggle: () -> Unit,
    onPinQuote: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { FigureHero(state = state, onPinToggle = onPinToggle) }

        if (state.quotes.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.figure_detail_no_quotes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(Res.string.figure_detail_quotes_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            items(state.quotes) { quote ->
                QuoteCard(
                    quoteText = quote.quoteText,
                    isPinned = quote.isPinned,
                    onPinQuote = { onPinQuote(quote.quoteText) },
                    footerText = quote.headlineTitle.takeIf { it.isNotBlank() }?.let { "In response to: $it" },
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun WritingsTabContent(state: FigureDetailContract.UiState.Success, onPinToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        FigureHero(state = state, onPinToggle = onPinToggle)

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(Res.string.figure_detail_tab_writings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.figure_detail_writings_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun FigureDetailScreenPreview(
    @PreviewParameter(FigureDetailStateProvider::class) state: FigureDetailContract.UiState
) {
    MediaSageTheme {
        FigureDetailScreen(state = state)
    }
}

// endregion
