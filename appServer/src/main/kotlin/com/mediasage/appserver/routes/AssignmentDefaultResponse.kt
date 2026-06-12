package com.mediasage.appserver.routes

import kotlinx.serialization.Serializable

@Serializable
data class AssignmentDefaultResponse(
    val dayOrdinal: Int,
    val figureName: String,
)
