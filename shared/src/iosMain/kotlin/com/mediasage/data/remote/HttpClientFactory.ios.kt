package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin)
