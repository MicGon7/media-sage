package com.mediasage.server.di

import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.NewsApiService
import com.mediasage.server.service.ScriptureApiService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun serverModule(claudeApiKey: String, newsApiKey: String, scriptureApiKey: String) = module {
    // HTTP client for outbound API calls (Claude, News, Scripture)
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    // Claude API service
    single { ClaudeApiService(get(), claudeApiKey) }

    // News API service
    single { NewsApiService(get(), newsApiKey) }

    // Scripture API service
    single { ScriptureApiService(get(), scriptureApiKey) }
}
