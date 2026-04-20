package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp)
