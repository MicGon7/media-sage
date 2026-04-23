package com.mediasage.data.repository

import com.mediasage.data.mapper.toDomain
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.EncouragementRepository

class EncouragementRepositoryImpl(
    private val api: MediaSageApi
) : EncouragementRepository {

    override suspend fun getEncouragement(
        headlineTitle: String,
        articleUrl: String?
    ): Encouragement {
        val dto = api.encourage(
            EncourageRequestDto(
                headlineTitle = headlineTitle,
                articleUrl = articleUrl
            )
        )
        return dto.toDomain()
    }
}
