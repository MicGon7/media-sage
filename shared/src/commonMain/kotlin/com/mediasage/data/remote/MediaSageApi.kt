package com.mediasage.data.remote

import com.mediasage.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

/** Client-side API service for communicating with the Media Sage server. */
interface MediaSageApi {
    suspend fun getHeadlines(locale: String = "us", limit: Int = 10): List<NewsArticleDto>
    suspend fun searchNews(query: String, limit: Int = 10): List<NewsArticleDto>
    suspend fun encourage(request: EncourageRequestDto): EncourageResultDto
    fun encourageStream(request: EncourageRequestDto): Flow<StreamEvent>
    @Deprecated("Use encourage instead — TODO MS-46")
    suspend fun matchQuote(request: MatchRequestDto): MatchResultDto
    suspend fun searchScripture(query: String, limit: Int = 10): List<ScriptureVerseDto>
    suspend fun getPassage(passageId: String): ScripturePassageDto
}
