package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.logging.HttpLoggingInterceptor

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
    }
}
