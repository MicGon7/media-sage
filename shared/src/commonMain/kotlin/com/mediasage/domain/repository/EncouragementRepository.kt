package com.mediasage.domain.repository

import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface EncouragementRepository {
    suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String = "",
        headlineImageUrl: String? = null,
        articleUrl: String?
    ): Encouragement

    fun streamEncouragement(
        headlineTitle: String,
        headlineSource: String = "",
        headlineImageUrl: String? = null,
        articleUrl: String?
    ): Flow<StreamEvent>
}
