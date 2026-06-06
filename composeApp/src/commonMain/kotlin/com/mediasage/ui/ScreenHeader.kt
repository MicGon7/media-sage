package com.mediasage.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sticky collapsing screen header. No internal horizontal padding — the caller's sticky header
 * wrapper owns all padding so title, subtitle, divider, and stickyContent all align uniformly.
 *
 * Layout:
 *   1. Title — animates 32sp → 18sp on scroll
 *   2. [subtitle] — visible expanded, hidden collapsed, sits ABOVE the divider
 *   3. Divider — omit via showDivider = false
 *   4. [stickyContent] — always shown BELOW the divider
 */
@Composable
fun ScreenHeader(
    title: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    expandedTitleSize: Float = TITLE_SIZE_EXPANDED,
    subtitle: @Composable (() -> Unit)? = null,
    stickyContent: @Composable (() -> Unit)? = null
) {
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val titleSize by animateFloatAsState(
        targetValue = if (collapsed) TITLE_SIZE_COLLAPSED else expandedTitleSize,
        label = "headerTitleSize"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = titleSize.sp,
            fontWeight = if (expandedTitleSize >= TITLE_SIZE_EXPANDED) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        if (subtitle != null) {
            AnimatedVisibility(visible = !collapsed) {
                subtitle()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )
        }
        stickyContent?.invoke()
    }
}

private const val TITLE_SIZE_EXPANDED = 32f
private const val TITLE_SIZE_COLLAPSED = 18f
