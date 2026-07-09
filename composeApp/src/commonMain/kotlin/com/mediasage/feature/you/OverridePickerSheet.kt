package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.domain.model.Figure
import com.mediasage.ui.FigurePlaceholder
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_picker_clear_day
import mediasage.composeapp.generated.resources.you_picker_empty
import mediasage.composeapp.generated.resources.you_picker_search_clear
import mediasage.composeapp.generated.resources.you_picker_search_hint
import mediasage.composeapp.generated.resources.you_picker_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
internal fun OverridePickerSheet(
    figures: List<Figure>,
    currentFigureId: Long?,
    onFigureSelected: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(figures, query) {
        if (query.isBlank()) figures else figures.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.you_picker_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OverrideSearchField(query = query, onQueryChange = { query = it })
        Spacer(modifier = Modifier.height(8.dp))
        OverrideFigureList(
            filtered = filtered,
            currentFigureId = currentFigureId,
            onFigureSelected = onFigureSelected,
            onClear = onClear,
        )
    }
}

@Composable
private fun OverrideSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.you_picker_search_hint)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.you_picker_search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun OverrideFigureList(
    filtered: List<Figure>,
    currentFigureId: Long?,
    onFigureSelected: (Long) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (currentFigureId != null) {
            item { OverrideClearRow(onClear = onClear) }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.you_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            items(filtered, key = { it.id }) { figure ->
                OverrideFigureRow(figure = figure, isSelected = figure.id == currentFigureId, onTap = onFigureSelected)
            }
        }
    }
}

@Composable
private fun OverrideClearRow(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClear)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        }
        Text(stringResource(Res.string.you_picker_clear_day), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun OverrideFigureRow(figure: Figure, isSelected: Boolean, onTap: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(figure.id) }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(figure.name, style = MaterialTheme.typography.bodyLarge)
            if (figure.role.isNotBlank()) {
                Text(figure.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
