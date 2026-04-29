package com.mediasage.domain.repository

import com.mediasage.domain.model.Encouragement

interface EncouragementRepository {
    suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String = "",
        headlineImageUrl: String? = null,
        articleUrl: String?,
        articleSnippet: String? = null
    ): Encouragement
}
