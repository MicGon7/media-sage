package com.mediasage.data.mapper

import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.FigureDto
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.model.Quote
import kotlin.time.Instant

// Figure
fun FigureEntity.toDomain() = Figure(
    id = id,
    name = name,
    category = FigureCategory.fromString(category),
    century = century,
    bio = bio,
    role = role,
    lifespan = lifespan,
    themes = if (themes.isEmpty()) emptyList() else themes.split(",").map { it.trim() },
    portraitUrl = portraitUrl,
    serverId = serverId
)

fun Figure.toEntity() = FigureEntity(
    id = id,
    name = name,
    category = category.name.lowercase(),
    century = century,
    bio = bio,
    role = role,
    lifespan = lifespan,
    themes = themes.joinToString(","),
    portraitUrl = portraitUrl,
    serverId = serverId
)

fun FigureDto.toEntity() = FigureEntity(
    id = id,
    name = name,
    category = category,
    century = century,
    bio = bio,
    role = role,
    lifespan = lifespan,
    themes = themes,
    portraitUrl = portraitUrl,
    serverId = id
)

// Quote
fun QuoteEntity.toDomain() = Quote(
    id = id,
    figureId = figureId,
    text = text,
    source = source,
    themes = if (themes.isEmpty()) emptyList() else themes.split(",").map { it.trim() },
    verified = verified,
    memorized = memorized
)

fun Quote.toEntity() = QuoteEntity(
    id = id,
    figureId = figureId,
    text = text,
    source = source,
    themes = themes.joinToString(","),
    verified = verified,
    memorized = memorized
)

// DTO → Entity (API response → local storage)
fun NewsArticleDto.toEntity(fetchedAt: Long = 0L) = HeadlineEntity(
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl.ifBlank { null },
    publishedAt = runCatching { Instant.parse(publishedAt).toEpochMilliseconds() }.getOrDefault(fetchedAt),
    fetchedAt = fetchedAt,
    snippet = snippet.ifBlank { null },
    category = categories.firstOrNull().orEmpty()
)

// Headline — isRead lives in a separate per-user read_headlines table (MS-734), not on the
// entity, so it's always passed in explicitly rather than round-tripped through the entity.
fun HeadlineEntity.toDomain(isRead: Boolean = false) = Headline(
    id = id,
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    snippet = snippet,
    category = category,
    isRead = isRead
)

fun Headline.toEntity() = HeadlineEntity(
    id = id,
    title = title,
    source = source,
    url = url,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    snippet = snippet,
    category = category
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
    figureImageUrl = figureImageUrl,
    headlineTitle = headlineTitle,
    headlineSource = headlineSource,
    headlineImageUrl = headlineImageUrl,
    articleUrl = articleUrl,
    bookmarked = bookmarked,
    headlineCategory = headlineCategory,
    headlinePublishedAt = headlinePublishedAt
)

fun Encouragement.toEntity(
    articleUrl: String,
    headlineTitle: String = "",
    headlineSource: String = "",
    headlineImageUrl: String? = null,
    cachedAt: Long = 0L,
    figureId: Long? = null,
    headlineCategory: String = "",
    headlinePublishedAt: Long = 0L
) = EncouragementEntity(
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
    headlineSource = headlineSource,
    headlineImageUrl = headlineImageUrl,
    cachedAt = cachedAt,
    figureId = figureId,
    headlineCategory = headlineCategory,
    headlinePublishedAt = headlinePublishedAt
)
