package com.mediasage.agentruntime

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AnthropicClient(
    private val httpClient: HttpClient,
    private val authToken: String,
    baseUrl: String,
) {
    private val messagesUrl = "${baseUrl.trimEnd('/')}/v1/messages"

    suspend fun post(body: String): String {
        val response = httpClient.post(messagesUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $authToken")
            header("anthropic-version", AnthropicApi.VERSION)
            setBody(body)
        }
        check(response.status.isSuccess()) {
            "Claude API error (${response.status}): ${response.bodyAsText()}"
        }
        return response.bodyAsText()
    }
}
