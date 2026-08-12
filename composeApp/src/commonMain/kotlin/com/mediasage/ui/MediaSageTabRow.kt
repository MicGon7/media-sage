package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCaramel
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme

/**
 * Comic-palette bottom tab row shared by [com.mediasage.feature.figures.FigureDetailScreen] (Biography |
 * Quotes | Writings) and [com.mediasage.feature.daydetail.DayDetailScreen] (Morning | Evening).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSageTabRow(
    selectedIndex: Int,
    tabLabels: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    showBackground: Boolean = true,
    edgePadding: Dp = 52.dp,
    singleBottomIndicator: Boolean = false,
    tabHeight: Dp = 48.dp,
    labelStyle: TextStyle? = null,
) {
    val isDark = MediaSageTheme.isDark
    val gradientColors = if (isDark) listOf(ComicBrown, ComicInk) else listOf(ComicCream, ComicTan)
    val contentColor = if (isDark) ComicTan else ComicInk
    val indicatorColor = if (isDark) ComicCaramel else ComicBrown
    val rowModifier = if (showBackground) modifier.background(Brush.horizontalGradient(gradientColors)) else modifier

    val indicator: @Composable TabIndicatorScope.() -> Unit = {
        Box(
            modifier = Modifier
                .tabIndicatorOffset(selectedIndex, matchContentSize = true)
                .fillMaxHeight()
        ) {
            if (!singleBottomIndicator) {
                TabIndicatorBar(color = indicatorColor, modifier = Modifier.align(Alignment.TopStart))
            }
            TabIndicatorBar(color = indicatorColor, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
    val tabs: @Composable () -> Unit = {
        tabLabels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.height(tabHeight),
                text = {
                    if (labelStyle != null) {
                        Text(text = label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                selectedContentColor = contentColor,
                unselectedContentColor = contentColor.copy(alpha = 0.6f),
            )
        }
    }

    if (scrollable) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = rowModifier,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            edgePadding = edgePadding,
            indicator = indicator,
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = rowModifier,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            indicator = indicator,
            tabs = tabs,
        )
    }
}

private val TabIndicatorThickness = 3.dp

@Composable
private fun TabIndicatorBar(color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(TabIndicatorThickness)
            .background(color)
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageTabRowPreview() {
    MediaSageTheme {
        MediaSageTabRow(
            selectedIndex = 0,
            tabLabels = listOf("Morning", "Evening"),
            onTabSelected = {},
        )
    }
}

// endregion
