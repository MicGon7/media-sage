package com.mediasage.server.di

import com.mediasage.server.service.ClaudeApiService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun serverModule(claudeApiKey: String) = module {
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
}
