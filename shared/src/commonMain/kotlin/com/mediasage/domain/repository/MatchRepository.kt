package com.mediasage.domain.repository

import com.mediasage.domain.model.Match
import kotlinx.coroutines.flow.Flow

interface MatchRepository {
    fun observeAllMatches(): Flow<List<Match>>
    suspend fun getMatchForHeadline(headlineId: Long): Match?
    suspend fun requestMatch(headlineId: Long): Match
}
