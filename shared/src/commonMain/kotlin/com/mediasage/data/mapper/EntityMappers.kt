package com.mediasage.data.mapper

import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.MatchEntity
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.model.Match
import com.mediasage.domain.model.Quote

// Figure
fun FigureEntity.toDomain() = Figure(
    id = id,
    name = name,
    category = FigureCategory.fromString(category),
    century = century,
    description = description
)

fun Figure.toEntity() = FigureEntity(
    id = id,
    name = name,
    category = category.name.lowercase(),
    century = century,
    description = description
)

// Quote
fun QuoteEntity.toDomain() = Quote(
    id = id,
    figureId = figureId,
    text = text,
    source = source,
    themes = if (themes.isEmpty()) emptyList() else themes.split(",").map { it.trim() }
)

fun Quote.toEntity() = QuoteEntity(
    id = id,
    figureId = figureId,
    text = text,
    source = source,
    themes = themes.joinToString(",")
)

// Headline
fun HeadlineEntity.toDomain() = Headline(
    id = id,
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt
)

fun Headline.toEntity() = HeadlineEntity(
    id = id,
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt
)

// Match
fun MatchEntity.toDomain() = Match(
    id = id,
    headlineId = headlineId,
    quoteId = quoteId,
    explanation = explanation,
    confidence = confidence,
    connectionThemes = if (connectionThemes.isEmpty()) emptyList()
        else connectionThemes.split(",").map { it.trim() },
    createdAt = createdAt
)

fun Match.toEntity() = MatchEntity(
    id = id,
    headlineId = headlineId,
    quoteId = quoteId,
    explanation = explanation,
    confidence = confidence,
    connectionThemes = connectionThemes.joinToString(","),
    createdAt = createdAt
)
