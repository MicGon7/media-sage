package com.mediasage

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
