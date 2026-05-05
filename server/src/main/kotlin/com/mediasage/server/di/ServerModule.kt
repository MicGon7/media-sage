package com.mediasage.server.di

import com.mediasage.server.repository.FigureRepository
import com.mediasage.server.repository.QuoteRepository
import com.mediasage.server.service.ArticleScraperService
import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.DailyReflectionService
import com.mediasage.server.service.NewsApiService
import com.mediasage.server.service.ScriptureApiService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun serverModule(
    claudeApiKey: String,
    newsApiKey: String,
    scriptureApiKey: String,
    baseUrl: String
) = module {
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    single { ClaudeApiService(get(), claudeApiKey) }
    single { NewsApiService(get(), newsApiKey) }
    single { ScriptureApiService(get(), scriptureApiKey) }
    single { ArticleScraperService() }
    single { FigureRepository(baseUrl) }
    single { QuoteRepository() }
    single { DailyReflectionService(get(), get()) }
}
