package com.mediasage.server.routes

import kotlinx.serialization.Serializable

@Serializable
data class AssignmentDefaultResponse(
    val dayOrdinal: Int,
    val figureName: String,
)
