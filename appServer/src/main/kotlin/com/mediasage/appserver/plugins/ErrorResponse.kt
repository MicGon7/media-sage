package com.mediasage.appserver.plugins

import kotlinx.serialization.Serializable

/** Standard error response returned by all endpoints. */
@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String
)
