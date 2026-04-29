package com.mediasage.data.remote

import com.mediasage.domain.model.StreamEvent
import com.mediasage.domain.model.StreamField
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SseDeltaPayload(val field: String, val text: String)

private val sseJson = Json { ignoreUnknownKeys = true }

class MediaSageApiImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : MediaSageApi {

    private val streamingClient: HttpClient by lazy {
        httpClient.config {
            install(HttpTimeout) {
                socketTimeoutMillis = 120_000
                requestTimeoutMillis = Long.MAX_VALUE
            }
        }
    }

    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> {
        return httpClient.get("$baseUrl/api/news/headlines") {
            parameter("locale", locale)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> {
        return httpClient.get("$baseUrl/api/news/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto {
        return httpClient.post("$baseUrl/api/analysis/encourage") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override fun encourageStream(request: EncourageRequestDto): Flow<StreamEvent> = channelFlow {
        streamingClient.preparePost("$baseUrl/api/analysis/encourage/stream") {
            contentType(ContentType.Application.Json)
            setBody(sseJson.encodeToString(EncourageRequestDto.serializer(), request))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            var currentEventType: String? = null

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("event: ") -> currentEventType = line.removePrefix("event: ")
                    line.startsWith("data: ") -> {
                        val data = line.removePrefix("data: ")
                        when (currentEventType) {
                            "delta" -> {
                                runCatching {
                                    val payload = sseJson.decodeFromString<SseDeltaPayload>(data)
                                    val field = StreamField.valueOf(payload.field)
                                    send(StreamEvent.FieldDelta(field, payload.text))
                                }
                            }
                            "portrait" -> send(StreamEvent.Portrait(data))
                            "done" -> send(StreamEvent.Done)
                        }
                        currentEventType = null
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto {
        return httpClient.post("$baseUrl/api/analysis/match") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> {
        return httpClient.get("$baseUrl/api/scripture/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun getPassage(passageId: String): ScripturePassageDto {
        return httpClient.get("$baseUrl/api/scripture/passage/$passageId").body()
    }
}
