package com.mediasage.domain.model

data class UserSession(
    val userId: String,
    val email: String?,
    val displayName: String? = null,
)
