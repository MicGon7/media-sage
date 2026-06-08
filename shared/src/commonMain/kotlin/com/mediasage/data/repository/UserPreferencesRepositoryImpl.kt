package com.mediasage.data.repository

import com.mediasage.data.local.dao.UserPreferencesDao
import com.mediasage.data.local.entity.UserPreferencesEntity
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepositoryImpl(
    private val dao: UserPreferencesDao,
) : UserPreferencesRepository {

    override fun observeLens(): Flow<LensFilter> =
        dao.observe().map { entity ->
            entity?.selectedLens?.let { runCatching { LensFilter.valueOf(it) }.getOrNull() }
                ?: LensFilter.NEWS
        }

    override suspend fun saveLens(lens: LensFilter) {
        dao.upsert(UserPreferencesEntity(selectedLens = lens.name))
    }

    override suspend fun initializeIfAbsent() {
        if (dao.get() == null) dao.upsert(UserPreferencesEntity())
    }
}
