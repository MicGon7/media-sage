package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(20, TimeUnit.SECONDS)
            writeTimeout(20, TimeUnit.SECONDS)
        }
        addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
    }
}
