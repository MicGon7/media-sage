package com.mediasage.ui

import kotlinx.io.IOException

enum class ErrorType {
    NETWORK,
    GENERIC
}

fun Exception.toErrorType(): ErrorType =
    when (this) {
        is IOException -> ErrorType.NETWORK
        else -> ErrorType.GENERIC
    }
