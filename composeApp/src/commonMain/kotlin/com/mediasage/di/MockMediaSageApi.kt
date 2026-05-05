package com.mediasage.di

import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.DailyReflectionResponseDto
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.FiguresResponse
import com.mediasage.data.remote.MatchRequestDto
import com.mediasage.data.remote.MatchResultDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.data.remote.ScripturePassageDto
import com.mediasage.data.remote.ScriptureVerseDto

/** Temporary mock API for physical device demos without a server. */
class MockMediaSageApi : MediaSageApi {

    override suspend fun getFigures(since: Long?): FiguresResponse = FiguresResponse(syncedAt = 0L, figures = emptyList())

    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> =
        MockData.headlines

    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> =
        MockData.headlines.filter { it.title.contains(query, ignoreCase = true) }

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto {
        val headline = MockData.headlines.find { it.title == request.headlineTitle }
        return MockData.encourageResultForHeadline(headline?.uuid ?: "1")
    }

    @Deprecated("Use encourage instead — TODO MS-46")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto =
        throw NotImplementedError("Deprecated")

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> =
        emptyList()

    override suspend fun getPassage(passageId: String): ScripturePassageDto =
        ScripturePassageDto(id = passageId)

    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto =
        DailyReflectionResponseDto(
            scriptureReference = "Psalm 46:10",
            scriptureText = "Be still, and know that I am God.",
            reflection = "In the noise of this world, the call to stillness is a revolutionary act of faith.",
            sources = emptyList(),
            tone = request.tone
        )
}
