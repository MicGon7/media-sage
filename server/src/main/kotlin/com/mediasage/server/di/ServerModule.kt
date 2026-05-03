package com.mediasage.server.di

import com.mediasage.server.repository.FigureRepository
import com.mediasage.server.service.AgentLaunchService
import com.mediasage.server.service.ArticleScraperService
import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.JiraApiService
import com.mediasage.server.service.JiraLabelChecker
import com.mediasage.server.service.NewsApiService
import com.mediasage.server.service.ScriptureApiService
import com.mediasage.server.service.WikimediaService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun serverModule(
    claudeApiKey: String,
    newsApiKey: String,
    scriptureApiKey: String,
    agentRepoPath: String,
    jiraConfig: JiraConfig,
    scope: CoroutineScope,
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
    single { WikimediaService(get()) }
    single { AgentLaunchService(agentRepoPath, scope) }
    single<JiraLabelChecker> { JiraApiService(get(), jiraConfig.cloudId, jiraConfig.email, jiraConfig.apiToken) }
    single { FigureRepository(baseUrl) }
}
