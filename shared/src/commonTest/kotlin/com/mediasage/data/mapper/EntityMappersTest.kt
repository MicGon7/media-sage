package com.mediasage.data.mapper

import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.MatchEntity
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.data.remote.FigureDto
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.model.Match
import com.mediasage.domain.model.Quote
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMappersTest {

    @Test
    fun figureEntityToDomain() {
        val entity = FigureEntity(
            id = 1, name = "Augustine", category = "theologian",
            century = "4th", bio = "Bishop of Hippo",
            role = "Bishop & Church Father", lifespan = "354-430"
        )
        val domain = entity.toDomain()
        assertEquals(1, domain.id)
        assertEquals("Augustine", domain.name)
        assertEquals(FigureCategory.THEOLOGIAN, domain.category)
        assertEquals("4th", domain.century)
        assertEquals("Bishop & Church Father", domain.role)
        assertEquals("354-430", domain.lifespan)
        assertEquals("Bishop of Hippo", domain.bio)
    }

    @Test
    fun figureDtoToEntityUsesServerIdAsPrimaryKey() {
        val dto = FigureDto(
            id = 42L, name = "Augustine", category = "theologian",
            century = "4th", role = "Bishop", lifespan = "354-430", bio = "Bishop of Hippo"
        )
        val entity = dto.toEntity()
        assertEquals(42L, entity.id)
        assertEquals(42L, entity.serverId)
    }

    @Test
    fun figureDomainToEntity() {
        val domain = Figure(
            id = 1, name = "Augustine", category = FigureCategory.THEOLOGIAN,
            century = "4th", bio = "Bishop of Hippo",
            role = "Bishop & Church Father", lifespan = "354-430"
        )
        val entity = domain.toEntity()
        assertEquals("theologian", entity.category)
        assertEquals("Bishop & Church Father", entity.role)
        assertEquals("354-430", entity.lifespan)
        assertEquals("Bishop of Hippo", entity.bio)
    }

    @Test
    fun figureRoundTrip() {
        val entity = FigureEntity(
            id = 1, name = "Bonhoeffer", category = "theologian",
            century = "20th", bio = "German pastor",
            role = "Theologian & Martyr", lifespan = "1906-1945"
        )
        val domain = entity.toDomain()
        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun figureDefaultsForNewFields() {
        val entity = FigureEntity(
            id = 1, name = "Augustine", category = "theologian", century = "4th"
        )
        val domain = entity.toDomain()
        assertEquals("", domain.role)
        assertEquals("", domain.lifespan)
        assertEquals("", domain.bio)
        assertEquals(emptyList(), domain.themes)
        assertEquals(null, domain.portraitUrl)
    }

    @Test
    fun figureThemesRoundTrip() {
        val entity = FigureEntity(
            id = 1, name = "Augustine", category = "theologian",
            century = "4th", themes = "grace,faith,redemption"
        )
        val domain = entity.toDomain()
        assertEquals(listOf("grace", "faith", "redemption"), domain.themes)
        assertEquals("grace,faith,redemption", domain.toEntity().themes)
    }

    @Test
    fun figureEntityToDomainDeduplicatesThemesCaseInsensitively() {
        val entity = FigureEntity(
            id = 1, name = "Augustine", category = "theologian",
            century = "4th", themes = "Grace,grace,faith"
        )
        val domain = entity.toDomain()
        assertEquals(listOf("Grace", "faith"), domain.themes)
    }

    @Test
    fun figureDtoToEntityDeduplicatesThemesCaseInsensitively() {
        val dto = FigureDto(
            id = 42L, name = "Augustine", category = "theologian",
            century = "4th", themes = "Grace,grace,faith"
        )
        val entity = dto.toEntity()
        assertEquals("Grace,faith", entity.themes)
    }

    @Test
    fun quoteEntityToDomainSplitsThemes() {
        val entity = QuoteEntity(
            id = 1, figureId = 1, text = "Test quote",
            source = "Confessions", themes = "faith,hope,love"
        )
        val domain = entity.toDomain()
        assertEquals(listOf("faith", "hope", "love"), domain.themes)
    }

    @Test
    fun quoteEntityToDomainHandlesEmptyThemes() {
        val entity = QuoteEntity(
            id = 1, figureId = 1, text = "Test quote",
            source = "Confessions", themes = ""
        )
        val domain = entity.toDomain()
        assertEquals(emptyList(), domain.themes)
    }

    @Test
    fun quoteDomainToEntityJoinsThemes() {
        val domain = Quote(
            id = 1, figureId = 1, text = "Test quote",
            source = "Confessions", themes = listOf("faith", "hope", "love")
        )
        val entity = domain.toEntity()
        assertEquals("faith,hope,love", entity.themes)
    }

    @Test
    fun quoteEntityToDomainDeduplicatesThemesCaseInsensitively() {
        val entity = QuoteEntity(
            id = 1, figureId = 1, text = "Test quote",
            source = "Confessions", themes = "Faith,faith,hope"
        )
        val domain = entity.toDomain()
        assertEquals(listOf("Faith", "hope"), domain.themes)
    }

    @Test
    fun quoteDomainToEntityDeduplicatesThemesCaseInsensitively() {
        val domain = Quote(
            id = 1, figureId = 1, text = "Test quote",
            source = "Confessions", themes = listOf("Faith", "faith", "hope")
        )
        val entity = domain.toEntity()
        assertEquals("Faith,hope", entity.themes)
    }

    @Test
    fun headlineRoundTrip() {
        val entity = HeadlineEntity(
            id = 1, title = "Test headline", source = "BBC",
            url = "https://example.com", imageUrl = null,
            publishedAt = 1000L, fetchedAt = 2000L
        )
        val domain = entity.toDomain()
        val backToEntity = domain.toEntity()
        assertEquals(entity, backToEntity)
    }

    @Test
    fun matchEntityToDomainSplitsConnectionThemes() {
        val entity = MatchEntity(
            id = 1, headlineId = 1, quoteId = 1,
            explanation = "Test", confidence = 0.9f,
            connectionThemes = "suffering,hope", createdAt = 1000L
        )
        val domain = entity.toDomain()
        assertEquals(listOf("suffering", "hope"), domain.connectionThemes)
    }

    @Test
    fun figureCategoryFromStringHandlesUnknown() {
        assertEquals(FigureCategory.THEOLOGIAN, FigureCategory.fromString("unknown"))
        assertEquals(FigureCategory.MYSTIC, FigureCategory.fromString("mystic"))
        assertEquals(FigureCategory.MISSIONARY, FigureCategory.fromString("missionary"))
        assertEquals(FigureCategory.CHURCH_FATHER, FigureCategory.fromString("church_father"))
        assertEquals(FigureCategory.SOCIAL_JUSTICE, FigureCategory.fromString("social_justice"))
        assertEquals(FigureCategory.INTELLECTUAL, FigureCategory.fromString("intellectual"))
    }
}
