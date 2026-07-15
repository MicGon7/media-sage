package com.mediasage.appserver.di

import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.repository.QuoteRepository
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.ClaudeApiClient
import com.mediasage.appserver.service.DailyReflectionService
import com.mediasage.appserver.service.NewsApiClient
import com.mediasage.appserver.service.ScriptureApiClient
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

    single { ClaudeApiClient(get(), claudeApiKey) }
    single { NewsApiClient(get(), newsApiKey) }
    single { ScriptureApiClient(get(), scriptureApiKey) }
    single { ArticleScraperService() }
    single { FigureRepository(baseUrl) }
    single { QuoteRepository() }
    single { DailyReflectionService(get(), get()) }
}
