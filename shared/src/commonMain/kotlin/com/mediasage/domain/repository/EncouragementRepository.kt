package com.mediasage.domain.repository

import com.mediasage.domain.model.Encouragement

interface EncouragementRepository {
    suspend fun getEncouragement(headlineTitle: String, articleUrl: String?): Encouragement
}
