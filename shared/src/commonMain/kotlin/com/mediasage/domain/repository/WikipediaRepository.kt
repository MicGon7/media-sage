package com.mediasage.domain.repository

interface WikipediaRepository {
    suspend fun getBio(figureName: String): String?
}
