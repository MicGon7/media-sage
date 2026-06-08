package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.domain.model.Figure
import com.mediasage.theme.AppTheme
import com.mediasage.theme.BrandAmber
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.ScreenHeader
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_carousel_assign_hint
import mediasage.composeapp.generated.resources.you_carousel_title
import mediasage.composeapp.generated.resources.you_lens_faith
import mediasage.composeapp.generated.resources.you_lens_grace
import mediasage.composeapp.generated.resources.you_lens_grief
import mediasage.composeapp.generated.resources.you_lens_hope
import mediasage.composeapp.generated.resources.you_lens_justice
import mediasage.composeapp.generated.resources.you_lens_love
import mediasage.composeapp.generated.resources.you_lens_perseverance
import mediasage.composeapp.generated.resources.you_lens_repentance
import mediasage.composeapp.generated.resources.you_lens_section_title

import mediasage.composeapp.generated.resources.you_lens_today
import mediasage.composeapp.generated.resources.you_nav_history
import mediasage.composeapp.generated.resources.you_nav_saved
import mediasage.composeapp.generated.resources.you_picker_clear_day
import mediasage.composeapp.generated.resources.you_picker_empty
import mediasage.composeapp.generated.resources.you_picker_search_clear
import mediasage.composeapp.generated.resources.you_picker_search_hint
import mediasage.composeapp.generated.resources.you_picker_title
import mediasage.composeapp.generated.resources.you_quote_card_header
import mediasage.composeapp.generated.resources.you_saved_section_title
import mediasage.composeapp.generated.resources.you_saved_see_all
import mediasage.composeapp.generated.resources.you_screen_title
import mediasage.composeapp.generated.resources.you_settings_icon_description
import com.mediasage.domain.model.LensFilter
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderContract.UiState,
    onIntent: (ReaderContract.Intent) -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFigureDetail: (figureId: Long) -> Unit = {},
) {
    if (state is ReaderContract.UiState.Ready && state.pickerOpenForDay != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isAssigned = state.weekSlots.any {
            it.dayOfWeek.ordinal == state.pickerOpenForDay && it.assignedFigureName != null
        }
        ModalBottomSheet(
            onDismissRequest = { onIntent(ReaderContract.Intent.PickerDismissed) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FigurePickerSheet(
                figures = state.pickerFigures,
                showClearOption = isAssigned,
                onFigureSelected = { figure ->
                    onIntent(ReaderContract.Intent.FigureAssigned(state.pickerOpenForDay, figure.id))
                },
                onClearDay = {
                    onIntent(ReaderContract.Intent.AssignmentCleared(state.pickerOpenForDay))
                },
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        ScreenHeader(
                            title = stringResource(Res.string.you_screen_title),
                            listState = listState,
                            showDivider = false,
                            expandedTitleSize = 24f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        )
                        // padding(top) matches ScreenHeader's internal title padding(top = 12.dp)
                        // so the icons sit at the same visual midpoint as the title text.
                        Row(modifier = Modifier.padding(top = 12.dp)) {
                            IconButton(onClick = onNavigateToBookmarks) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = stringResource(Res.string.you_nav_saved),
                                )
                            }
                            IconButton(onClick = onNavigateToHistory) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = stringResource(Res.string.you_nav_history),
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(Res.string.you_settings_icon_description),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
                }
            }

            if (state is ReaderContract.UiState.Ready) {
                item {
                    ReporterCarousel(
                        slots = state.weekSlots,
                        onDayTapped = { index -> onIntent(ReaderContract.Intent.DaySlotTapped(index)) },
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                item {
                    LensChipRow(
                        selectedLens = state.selectedLens,
                        onLensSelected = { onIntent(ReaderContract.Intent.LensSelected(it)) },
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }

                state.quoteCard?.let { quote ->
                    item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    item {
                        SavedQuoteCard(
                            quote = quote,
                            selectedLens = state.selectedLens,
                            onViewMore = { if (it > 0) onNavigateToFigureDetail(it) },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ReporterCarousel(
    slots: List<ReaderContract.DaySlot>,
    onDayTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayIndex = remember(slots) { slots.indexOfFirst { it.isToday } }
    val rowState = rememberLazyListState()
    val density = LocalDensity.current

    // After first layout, scroll so today's slot is horizontally centered.
    LaunchedEffect(todayIndex) {
        if (todayIndex < 0) return@LaunchedEffect
        snapshotFlow { rowState.layoutInfo.viewportSize.width }
            .filter { it > 0 }
            .first()
            .let { viewportWidth ->
                val itemWidthPx = with(density) { 72.dp.roundToPx() }
                val offset = -(viewportWidth / 2 - itemWidthPx / 2)
                rowState.scrollToItem(index = todayIndex, scrollOffset = offset)
            }
    }

    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_carousel_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(slots.size) { index ->
                DaySlotItem(slot = slots[index], onClick = { onDayTapped(index) })
            }
        }
    }
}

@Composable
private fun DaySlotItem(slot: ReaderContract.DaySlot, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = if (slot.isToday) primary else onSurfaceVariant

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = slot.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = labelColor,
            fontWeight = if (slot.isToday) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )

        ReporterCircle(
            slot = slot,
            size = 72.dp,
            primaryColor = primary,
            todayRingColor = BrandAmber,
            surfaceVariantColor = surfaceVariant,
            onSurfaceVariantColor = onSurfaceVariant,
        )

        Text(
            text = slot.assignedFigureName ?: stringResource(Res.string.you_carousel_assign_hint),
            style = MaterialTheme.typography.labelSmall,
            color = if (slot.assignedFigureName != null) MaterialTheme.colorScheme.onSurface else onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ReporterCircle(
    slot: ReaderContract.DaySlot,
    size: Dp,
    primaryColor: Color,
    todayRingColor: Color,
    surfaceVariantColor: Color,
    onSurfaceVariantColor: Color,
) {
    val isAssigned = slot.assignedFigureName != null

    when {
        slot.assignedFigureImageUrl != null -> {
            AsyncImage(
                model = slot.assignedFigureImageUrl,
                contentDescription = slot.assignedFigureName,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .then(
                        if (slot.isToday) Modifier.solidCircleBorder(todayRingColor, 2.dp)
                        else Modifier
                    ),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        isAssigned -> {
            val bgColor = if (slot.isToday) primaryColor else surfaceVariantColor.copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(bgColor)
                    .then(
                        if (slot.isToday) Modifier.solidCircleBorder(todayRingColor, 2.dp)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "†",
                    fontSize = 28.sp,
                    color = if (slot.isToday) primaryColor.copy(alpha = 0.4f) else onSurfaceVariantColor,
                )
            }
        }
        else -> {
            val borderColor = if (slot.isToday) todayRingColor else onSurfaceVariantColor.copy(alpha = 0.4f)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .dashedCircleBorder(borderColor, 1.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    color = borderColor,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

private fun Modifier.dashedCircleBorder(color: Color, strokeWidth: Dp): Modifier = drawBehind {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - strokeWidth.toPx() / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = strokeWidth.toPx(), pathEffect = pathEffect),
    )
}

private fun Modifier.solidCircleBorder(color: Color, strokeWidth: Dp): Modifier = drawBehind {
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - strokeWidth.toPx() / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = strokeWidth.toPx()),
    )
}

@Composable
private fun LensChipRow(
    selectedLens: LensFilter,
    onLensSelected: (LensFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_lens_section_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LensFilter.entries.forEach { lens ->
                item(key = lens.name) {
                    LensChip(
                        lens = lens,
                        selected = selectedLens == lens,
                        onClick = { onLensSelected(lens) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LensChip(
    lens: LensFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lensColor = lens.color()
    val onChip = if (lensColor.luminance() > 0.4f) Color(0xFF1A1A1A) else Color.White
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = stringResource(lens.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = lensColor,
            selectedLabelColor = onChip,
            labelColor = lensColor,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = lensColor.copy(alpha = 0.5f),
            selectedBorderColor = Color.Transparent,
            borderWidth = 1.5.dp,
        ),
    )
}

private fun LensFilter.labelRes() = when (this) {
    LensFilter.NEWS -> Res.string.you_lens_today
    LensFilter.LOVE -> Res.string.you_lens_love
    LensFilter.GRACE -> Res.string.you_lens_grace
    LensFilter.REPENTANCE -> Res.string.you_lens_repentance
    LensFilter.HOPE -> Res.string.you_lens_hope
    LensFilter.JUSTICE -> Res.string.you_lens_justice
    LensFilter.GRIEF -> Res.string.you_lens_grief
    LensFilter.FAITH -> Res.string.you_lens_faith
    LensFilter.PERSEVERANCE -> Res.string.you_lens_perseverance
}

@Composable
private fun LensFilter.color(): Color = when (this) {
    LensFilter.NEWS -> MaterialTheme.colorScheme.primary
    LensFilter.LOVE -> Color(0xFFD4687A)
    LensFilter.GRACE -> Color(0xFF9B7DC8)
    LensFilter.REPENTANCE -> Color(0xFF7A9E7E)
    LensFilter.HOPE -> Color(0xFFE8B84B)
    LensFilter.JUSTICE -> Color(0xFF4A8C6A)
    LensFilter.GRIEF -> Color(0xFF5B7BA8)
    LensFilter.FAITH -> Color(0xFFB87333)
    LensFilter.PERSEVERANCE -> Color(0xFF8B6356)
}

@Composable
private fun SavedQuoteCard(
    quote: ReaderContract.QuoteCard,
    selectedLens: LensFilter,
    onViewMore: (figureId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_saved_section_title),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Row {
                // Amber bookmark ribbon — same language as pinned reporters
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            color = BrandAmber,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = MaterialTheme.shapes.large.topStart,
                                bottomStart = MaterialTheme.shapes.large.bottomStart,
                                topEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                            )
                        )
                )
                Column(modifier = Modifier.padding(16.dp)) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Text(
                    text = stringResource(Res.string.you_quote_card_header),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = if (isDark) BrandAmber else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "“${quote.quoteText}”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (quote.figureImageUrl != null) {
                        AsyncImage(
                            model = quote.figureImageUrl,
                            contentDescription = quote.figureName,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            error = rememberVectorPainter(Icons.Filled.Person),
                            fallback = rememberVectorPainter(Icons.Filled.Person),
                        )
                    } else {
                        com.mediasage.ui.FigurePlaceholder(name = quote.figureName, size = 32.dp)
                    }
                    Text(
                        text = "\u2014 ${quote.figureName}, ${quote.figureRole}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(Res.string.you_saved_see_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { onViewMore(quote.figureId) },
                )
            }
            }
        }
    }

}

@Composable
private fun FigurePickerSheet(
    figures: List<Figure>,
    showClearOption: Boolean,
    onFigureSelected: (Figure) -> Unit,
    onClearDay: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(figures, query) {
        if (query.isBlank()) figures
        else figures.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.you_picker_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.you_picker_search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.you_picker_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (showClearOption) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearDay)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = stringResource(Res.string.you_picker_clear_day),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.you_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(filtered, key = { it.id }) { figure ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFigureSelected(figure) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (figure.portraitUrl != null) {
                            AsyncImage(
                                model = figure.portraitUrl,
                                contentDescription = figure.name,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                error = rememberVectorPainter(Icons.Filled.Person),
                                fallback = rememberVectorPainter(Icons.Filled.Person),
                            )
                        } else {
                            FigurePlaceholder(name = figure.name, size = 40.dp)
                        }
                        Column {
                            Text(
                                text = figure.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (figure.role.isNotBlank()) {
                                Text(
                                    text = figure.role,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    MediaSageTheme {
        ReaderScreen(
            state = ReaderContract.UiState.Ready(
                weekSlots = previewWeekSlots(),
                selectedLens = LensFilter.NEWS,
                quoteCard = previewQuoteCard(),
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenDarkPreview() {
    MediaSageTheme(darkTheme = true) {
        ReaderScreen(
            state = ReaderContract.UiState.Ready(
                weekSlots = previewWeekSlots(),
                selectedLens = LensFilter.HOPE,
                quoteCard = previewQuoteCard(),
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenModernPreview() {
    MediaSageTheme(theme = AppTheme.MODERN) {
        ReaderScreen(
            state = ReaderContract.UiState.Ready(
                weekSlots = previewWeekSlots(),
                selectedLens = LensFilter.NEWS,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenWarmPreview() {
    MediaSageTheme(theme = AppTheme.WARM) {
        ReaderScreen(
            state = ReaderContract.UiState.Ready(
                weekSlots = previewWeekSlots(),
                selectedLens = LensFilter.JUSTICE,
            ),
            onIntent = {},
        )
    }
}

private fun previewWeekSlots() = listOf(
    DayOfWeek.MONDAY to "Augustine",
    DayOfWeek.TUESDAY to "Teresa of Ávila",
    DayOfWeek.WEDNESDAY to null,
    DayOfWeek.THURSDAY to "C.S. Lewis",
    DayOfWeek.FRIDAY to null,
    DayOfWeek.SATURDAY to null,
    DayOfWeek.SUNDAY to null,
).mapIndexed { i, (day, name) ->
    ReaderContract.DaySlot(
        dayOfWeek = day,
        isToday = i == 3,
        assignedFigureName = name,
    )
}

private fun previewQuoteCard() = ReaderContract.QuoteCard(
    quoteText = "You can't go back and change the beginning, but you can start where you are and change the ending.",
    figureName = "C.S. Lewis",
    figureRole = "Author & Apologist",
    figureImageUrl = null,
    figureId = -1L,
)

// endregion
