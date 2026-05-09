package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    engine {
        // Native NSURLSession timeout — mirrors Android OkHttp socket timeouts so both
        // platforms give up after 20 seconds of no data from a dead connection.
        requestTimeout = 20_000
    }
}
