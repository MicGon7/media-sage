package com.mediasage.feature.you

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.LensFilter
import com.mediasage.theme.AppTheme
import com.mediasage.theme.BrandAmber
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.LensFaith
import com.mediasage.theme.LensGrace
import com.mediasage.theme.LensGrief
import com.mediasage.theme.LensHope
import com.mediasage.theme.LensJustice
import com.mediasage.theme.LensLove
import com.mediasage.theme.LensPerseverance
import com.mediasage.theme.LensRepentance
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.MediaSageSurface
import com.mediasage.ui.ReassignConfirmationDialog
import com.mediasage.ui.ScreenHeader
import kotlinx.datetime.DayOfWeek
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reader_briefings_empty_subtitle
import mediasage.composeapp.generated.resources.reader_briefings_empty_title
import mediasage.composeapp.generated.resources.reader_quote_empty_subtitle
import mediasage.composeapp.generated.resources.reader_quote_empty_title
import mediasage.composeapp.generated.resources.saved_insights_banner
import mediasage.composeapp.generated.resources.you_carousel_assign_hint
import mediasage.composeapp.generated.resources.you_lens_faith
import mediasage.composeapp.generated.resources.you_lens_grace
import mediasage.composeapp.generated.resources.you_lens_grief
import mediasage.composeapp.generated.resources.you_lens_hope
import mediasage.composeapp.generated.resources.you_lens_justice
import mediasage.composeapp.generated.resources.you_lens_love
import mediasage.composeapp.generated.resources.you_lens_perseverance
import mediasage.composeapp.generated.resources.you_lens_repentance
import mediasage.composeapp.generated.resources.you_lens_today
import mediasage.composeapp.generated.resources.you_nav_saved
import mediasage.composeapp.generated.resources.you_picker_back_description
import mediasage.composeapp.generated.resources.you_picker_choose_theme
import mediasage.composeapp.generated.resources.you_picker_clear_day
import mediasage.composeapp.generated.resources.you_picker_empty
import mediasage.composeapp.generated.resources.you_picker_search_clear
import mediasage.composeapp.generated.resources.you_picker_search_hint
import mediasage.composeapp.generated.resources.you_picker_title
import mediasage.composeapp.generated.resources.you_quote_card_header
import mediasage.composeapp.generated.resources.you_saved_entry_subtitle
import mediasage.composeapp.generated.resources.you_saved_news_section_title
import mediasage.composeapp.generated.resources.you_recent_briefings_section_title
import mediasage.composeapp.generated.resources.you_saved_section_title
import mediasage.composeapp.generated.resources.you_saved_see_all
import mediasage.composeapp.generated.resources.you_screen_title
import mediasage.composeapp.generated.resources.you_settings_icon_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderContract.UiState,
    onIntent: (ReaderContract.Intent) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToQuotes: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToDayDetail: (epochDay: Long, figureName: String?, figureImageUrl: String?) -> Unit = { _, _, _ -> },
) {
    val ready = state as? ReaderContract.UiState.Ready
    val activeSheet = ready?.activeSheet as? ReaderContract.ActiveSheet.WeekSlotPicker
    if (ready != null && activeSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onIntent(ReaderContract.Intent.PickerDismissed) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            val isAssigned = ready.weekSlots.any {
                it.dayOfWeek.ordinal == activeSheet.dayOfWeek && it.assignedFigureName != null
            }
            FigurePickerSheet(
                figures = ready.pickerFigures,
                showClearOption = isAssigned,
                onFigureAndLensSelected = { figure, lens ->
                    onIntent(ReaderContract.Intent.FigureAssigned(activeSheet.dayOfWeek, figure.id, lens))
                },
                onClearDay = {
                    onIntent(ReaderContract.Intent.AssignmentCleared(activeSheet.dayOfWeek))
                },
            )
        }
    }

    ready?.pendingReassignment?.let { pending ->
        ReassignConfirmationDialog(
            currentFigureName = pending.currentFigureName,
            newFigureName = pending.newFigureName,
            nextWeekdayLabel = pending.nextWeekdayLabel,
            onConfirm = { onIntent(ReaderContract.Intent.ConfirmReassignment) },
            onDismiss = { onIntent(ReaderContract.Intent.CancelReassignment) },
            showSchedulerHint = false,
        )
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
                            subtitle = ready?.userDisplayName?.let { name ->
                                {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        )
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(Res.string.you_settings_icon_description),
                            )
                        }
                    }
                }
            }

            if (ready != null) {
                item {
                    ReporterScheduleSection(
                        weekSlots = ready.weekSlots,
                        onIntent = onIntent,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }

                item {
                    if (ready.quoteCard != null) {
                        SavedQuoteCard(
                            quote = ready.quoteCard,
                            onViewMore = onNavigateToQuotes,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        // Keep the populated card's section eyebrow so the page skeleton
                        // reads the same whether the section is filled or empty.
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionLabel(text = stringResource(Res.string.you_saved_section_title))
                            MediaSageEmptyState(
                                title = stringResource(Res.string.reader_quote_empty_title),
                                subtitle = stringResource(Res.string.reader_quote_empty_subtitle),
                            )
                        }
                    }
                }

                item {
                    if (ready.pastBriefings.isNotEmpty()) {
                        PastBriefingsCarousel(
                            cards = ready.pastBriefings,
                            onCardClick = { epochDay ->
                                val card = ready.pastBriefings.firstOrNull { it.epochDay == epochDay }
                                onNavigateToDayDetail(epochDay, card?.figureName, card?.figureImageUrl)
                            },
                            showSeeMore = ready.hasMorePastBriefings,
                            onSeeMore = onNavigateToHistory,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                            SectionLabel(text = stringResource(Res.string.you_recent_briefings_section_title))
                            MediaSageEmptyState(
                                title = stringResource(Res.string.reader_briefings_empty_title),
                                subtitle = stringResource(Res.string.reader_briefings_empty_subtitle),
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }

                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionLabel(text = stringResource(Res.string.you_saved_news_section_title))
                        SavedEntryCard(
                            title = stringResource(Res.string.you_nav_saved),
                            subtitle = stringResource(Res.string.you_saved_entry_subtitle),
                            onClick = onNavigateToBookmarks,
                        )
                    }
                }
            }

        }
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The Saved nav entry, built directly on [MediaSageSurface] rather than [MediaSageEntryCard] so
 * only this entry carries the decorative banner image, not every entry card in the app. */
@Composable
private fun SavedEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaSageSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 2.dp,
    ) { contentColor ->
        Column {
            Image(
                painter = painterResource(Res.drawable.saved_insights_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun DaySlotItem(
    slot: ReaderContract.DaySlot,
    onClick: () -> Unit,
) {
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

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                ReporterCircle(
                    slot = slot,
                    size = 72.dp,
                    primaryColor = primary,
                    todayRingColor = BrandAmber,
                    surfaceVariantColor = surfaceVariant,
                    onSurfaceVariantColor = onSurfaceVariant,
                )
            }
            if (slot.assignedLens != null) {
                LensBadge(
                    lens = slot.assignedLens,
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }

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
internal fun ReporterCircle(
    slot: ReaderContract.DaySlot,
    size: Dp,
    primaryColor: Color,
    todayRingColor: Color,
    surfaceVariantColor: Color,
    onSurfaceVariantColor: Color,
    showRing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isAssigned = slot.assignedFigureName != null

    when {
        slot.assignedFigureImageUrl != null -> {
            FigurePortraitImage(
                imageUrl = slot.assignedFigureImageUrl,
                name = slot.assignedFigureName,
                size = size,
                isToday = slot.isToday,
                showRing = showRing,
                modifier = modifier,
            )
        }
        isAssigned -> {
            val bgColor = if (slot.isToday) primaryColor else surfaceVariantColor.copy(alpha = 0.6f)
            Box(
                modifier = modifier
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
                modifier = modifier
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

internal fun Modifier.dashedCircleBorder(color: Color, strokeWidth: Dp): Modifier = drawBehind {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - strokeWidth.toPx() / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = strokeWidth.toPx(), pathEffect = pathEffect),
    )
}


@Composable
internal fun LensBadge(lens: LensFilter, modifier: Modifier = Modifier) {
    val color = lens.color()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color),
    )
}

internal fun LensFilter.labelRes() = when (this) {
    LensFilter.NEWS -> Res.string.you_lens_today
    LensFilter.LOVE -> Res.string.you_lens_love
    LensFilter.GRACE -> Res.string.you_lens_grace
    LensFilter.FAITH -> Res.string.you_lens_faith
    LensFilter.GRIEF -> Res.string.you_lens_grief
    LensFilter.REPENTANCE -> Res.string.you_lens_repentance
    LensFilter.HOPE -> Res.string.you_lens_hope
    LensFilter.JUSTICE -> Res.string.you_lens_justice
    LensFilter.PERSEVERANCE -> Res.string.you_lens_perseverance
}

@Composable
internal fun LensFilter.color(): Color = when (this) {
    LensFilter.NEWS -> MaterialTheme.colorScheme.primary
    LensFilter.LOVE -> LensLove
    LensFilter.GRACE -> LensGrace
    LensFilter.FAITH -> LensFaith
    LensFilter.GRIEF -> LensGrief
    LensFilter.REPENTANCE -> LensRepentance
    LensFilter.HOPE -> LensHope
    LensFilter.JUSTICE -> LensJustice
    LensFilter.PERSEVERANCE -> LensPerseverance
}

@Composable
private fun SavedQuoteCard(
    quote: ReaderContract.QuoteCard,
    onViewMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_saved_section_title),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val isDarkSurface = MediaSageTheme.isDark
        val cardSurface = if (isDarkSurface) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        } else {
            MaterialTheme.colorScheme.surface
        }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = cardSurface,
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column {
                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(Res.string.you_quote_card_header),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "“${quote.quoteText}”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(cardSurface, ComicCream),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(ComicCream, ComicTan)))
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (quote.figureImageUrl != null) {
                            AsyncImage(
                                model = quote.figureImageUrl,
                                contentDescription = quote.figureName,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                error = rememberVectorPainter(Icons.Filled.Person),
                                fallback = rememberVectorPainter(Icons.Filled.Person),
                            )
                        } else {
                            com.mediasage.ui.FigurePlaceholder(name = quote.figureName, size = 40.dp)
                        }
                        Text(
                            text = "— ${quote.figureName}, ${quote.figureRole}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ComicInk,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.you_saved_see_all),
                        style = MaterialTheme.typography.labelSmall,
                        color = ComicInk,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable(onClick = onViewMore),
                    )
                }
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FigurePickerSheet(
    figures: List<Figure>,
    showClearOption: Boolean,
    onFigureAndLensSelected: (Figure, LensFilter?) -> Unit,
    onClearDay: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFigure by remember { mutableStateOf<Figure?>(null) }
    val filtered = remember(figures, query) {
        if (query.isBlank()) figures
        else figures.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedFigure != null) {
            LensPickerSection(
                figure = selectedFigure!!,
                onLensSelected = { lens -> onFigureAndLensSelected(selectedFigure!!, lens) },
                onBack = { selectedFigure = null },
            )
        } else {
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
                                .clickable { selectedFigure = figure }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LensPickerSection(
    figure: Figure,
    onLensSelected: (LensFilter?) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.you_picker_back_description),
            )
            if (figure.portraitUrl != null) {
                AsyncImage(
                    model = figure.portraitUrl,
                    contentDescription = figure.name,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    error = rememberVectorPainter(Icons.Filled.Person),
                    fallback = rememberVectorPainter(Icons.Filled.Person),
                )
            } else {
                FigurePlaceholder(name = figure.name, size = 32.dp)
            }
            Text(text = figure.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SectionLabel(
            text = stringResource(Res.string.you_picker_choose_theme),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LensFilter.entries.forEach { lens ->
                val lensColor = lens.color()
                FilterChip(
                    selected = false,
                    onClick = { onLensSelected(if (lens == LensFilter.NEWS) null else lens) },
                    label = {
                        Text(
                            text = stringResource(lens.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = lensColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = lensColor.copy(alpha = 0.5f),
                        borderWidth = 1.5.dp,
                    ),
                )
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
            ),
            onIntent = {},
        )
    }
}

private fun previewWeekSlots() = listOf(
    DayOfWeek.MONDAY to Pair("Augustine", LensFilter.FAITH),
    DayOfWeek.TUESDAY to Pair("Teresa of Ávila", LensFilter.GRACE),
    DayOfWeek.WEDNESDAY to Pair(null, null),
    DayOfWeek.THURSDAY to Pair("C.S. Lewis", null),
    DayOfWeek.FRIDAY to Pair(null, null),
    DayOfWeek.SATURDAY to Pair(null, null),
    DayOfWeek.SUNDAY to Pair(null, null),
).mapIndexed { i, (day, figureLens) ->
    ReaderContract.DaySlot(
        dayOfWeek = day,
        epochDay = 0L,
        isToday = i == 3,
        assignedFigureName = figureLens.first,
        assignedLens = figureLens.second,
    )
}

private fun previewQuoteCard() = ReaderContract.QuoteCard(
    quoteText = "You can't go back and change the beginning, but you can start where you are and change the ending.",
    figureName = "C.S. Lewis",
    figureRole = "Author & Apologist",
    figureImageUrl = null,
)

// endregion
