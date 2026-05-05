package com.mediasage.scripts.service

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

class ImageGenerationService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val GENERATIONS_URL = "https://api.openai.com/v1/images/generations"
        private const val MODEL = "gpt-image-2"
        private const val SIZE = "1024x1024"

        private val responseJson = Json { ignoreUnknownKeys = true }
    }

    suspend fun generateTextOnly(
        figureName: String,
        figureRole: String,
        century: String,
        lifespan: String,
        quality: String = "low",
        promptDetail: String = ""
    ): ByteArray {
        val prompt = buildPrompt(figureName, figureRole, century, lifespan, promptDetail)
        val httpResponse = httpClient.post(GENERATIONS_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                ImageGenerationRequest(
                    model = MODEL,
                    prompt = prompt,
                    size = SIZE,
                    quality = quality
                )
            )
        }
        return decodeResponse(httpResponse.status.value, httpResponse.bodyAsText())
    }

    private fun buildPrompt(
        figureName: String,
        figureRole: String,
        century: String,
        lifespan: String,
        promptDetail: String = ""
    ): String = buildString {
        appendLine("A formal portrait of $figureName ($lifespan), $figureRole, $century century.")
        appendLine("Painted in the style of a Renaissance oil painting.")
        appendLine("Rich textures, soft candlelit lighting, dignified and contemplative expression.")
        appendLine("Chest-up composition against a dark neutral background.")
        appendLine("Historical Christian figure rendered in a classic master's style.")
        appendLine("Modest, period-appropriate attire. No exposed neckline or décolletage.")
        appendLine("No text, no frames, no borders.")
        if (promptDetail.isNotEmpty()) appendLine(promptDetail)
    }.trimEnd()

    private fun decodeResponse(statusCode: Int, body: String): ByteArray {
        if (statusCode !in 200..299) {
            throw ImageGenerationException(statusCode, "OpenAI image API error ($statusCode): $body")
        }
        val response = responseJson.decodeFromString<ImageApiResponse>(body)
        val b64 = response.data.firstOrNull()?.b64Json
            ?: throw ImageGenerationException(statusCode, "Empty image response from OpenAI")
        return Base64.getDecoder().decode(b64)
    }
}

// ---- DTOs ----

@Serializable
private data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String,
    val quality: String,
    @SerialName("response_format")
    val responseFormat: String = "b64_json",
    @SerialName("output_format")
    val outputFormat: String = "webp",
    @SerialName("output_compression")
    val outputCompression: Int = 85
)

@Serializable
private data class ImageApiResponse(val data: List<ImageData>)

@Serializable
private data class ImageData(
    @SerialName("b64_json")
    val b64Json: String = ""
)

class ImageGenerationException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)
