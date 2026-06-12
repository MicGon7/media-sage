package com.mediasage.appserver.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level

/** Logs every HTTP request with method, path, status, and duration. Uses Logback (configured in logback.xml). */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO              // Log at INFO level — visible in normal output
        disableDefaultColors()          // Cleaner logs in CI and file output
    }
}
