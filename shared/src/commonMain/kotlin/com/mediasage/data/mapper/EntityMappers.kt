package com.mediasage.data.mapper

import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.MatchEntity
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.domain.model.Encouragement
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
    description = description,
    role = role,
    lifespan = lifespan
)

fun Figure.toEntity() = FigureEntity(
    id = id,
    name = name,
    category = category.name.lowercase(),
    century = century,
    description = description,
    role = role,
    lifespan = lifespan
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

// DTO → Entity (API response → local storage)
fun NewsArticleDto.toEntity(fetchedAt: Long = 0L) = HeadlineEntity(
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl.ifBlank { null },
    publishedAt = fetchedAt, // Server returns ISO string; using fetchedAt as fallback
    fetchedAt = fetchedAt
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

// Encourage DTO → Domain
fun EncourageResultDto.toDomain() = Encouragement(
    summary = summary,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    explanation = explanation,
    connectionThemes = connectionThemes,
    matchTheme = matchTheme,
    tone = tone,
    figureImageUrl = figureImageUrl
)

// Encouragement Entity ↔ Domain
fun EncouragementEntity.toDomain() = Encouragement(
    summary = summary,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    explanation = explanation,
    connectionThemes = if (connectionThemes.isEmpty()) emptyList()
        else connectionThemes.split(",").map { it.trim() },
    matchTheme = matchTheme,
    tone = tone,
    figureImageUrl = figureImageUrl
)

fun Encouragement.toEntity(articleUrl: String, headlineTitle: String = "", cachedAt: Long = 0L) = EncouragementEntity(
    articleUrl = articleUrl,
    summary = summary,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    explanation = explanation,
    connectionThemes = connectionThemes.joinToString(","),
    matchTheme = matchTheme,
    tone = tone,
    figureImageUrl = figureImageUrl,
    headlineTitle = headlineTitle,
    cachedAt = cachedAt
)
