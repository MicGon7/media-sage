package com.mediasage.data.repository

import com.mediasage.data.local.dao.MatchDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.domain.model.Match
import com.mediasage.domain.repository.MatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MatchRepositoryImpl(
    private val matchDao: MatchDao
    // TODO: Add remote matching API service when MS-10 is complete
) : MatchRepository {

    override fun getAllMatches(): Flow<List<Match>> =
        matchDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMatchForHeadline(headlineId: Long): Match? =
        matchDao.getByHeadline(headlineId)?.toDomain()

    override suspend fun requestMatch(headlineId: Long): Match {
        // Check cache first
        matchDao.getByHeadline(headlineId)?.let { return it.toDomain() }

        // TODO: Call remote matching API, save result to Room (MS-10)
        // For now, throw until remote API is wired up
        throw NotImplementedError("Remote matching API not yet connected (MS-10)")
    }
}
