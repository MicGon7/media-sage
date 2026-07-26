package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.DayAssignmentEntity
import com.mediasage.data.local.entity.SyncMetaEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class DayAssignmentRepositoryImpl(
    private val dao: DayAssignmentDao,
    private val figureDao: FigureDao,
    private val api: MediaSageApi,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val remote: DayAssignmentRemoteDataSource?,
    private val syncMetaDao: SyncMetaDao,
    private val authRepository: AuthRepository,
) : DayAssignmentRepository {

    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> =
        dao.observeAll().map { entities ->
            entities.associate { entity ->
                entity.dayOfWeek to DayAssignment(
                    figureId = entity.figureId,
                    lens = entity.lens?.let { name -> LensFilter.entries.firstOrNull { it.name == name } },
                )
            }
        }

    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) {
        dao.upsert(DayAssignmentEntity(dayOfWeek = dayOfWeek, figureId = figureId, lens = lens?.name, synced = false))
        pushRow(dayOfWeek, figureId, lens?.name)
    }

    override suspend fun clear(dayOfWeek: Int) {
        dao.markPendingDelete(dayOfWeek)
        pushDelete(dayOfWeek)
    }

    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? =
        dailyReflectionRepository.getLockedFigureId(epochDay) ?: dao.getByDayOfWeek(dayOfWeek)?.figureId

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.countAll() > 0) return

        val defaults = withTimeoutOrNull(5_000) {
            try {
                api.getAssignmentDefaults().map { it.dayOrdinal to it.figureName }
            } catch (e: Exception) {
                null
            }
        } ?: FALLBACK_DEFAULTS

        for ((dayOrdinal, figureName) in defaults) {
            val figure = figureDao.getByNameIgnoreCase(figureName) ?: continue
            dao.upsert(DayAssignmentEntity(dayOfWeek = dayOrdinal, figureId = figure.id, synced = false))
        }
    }

    override suspend fun syncWithRemote(userId: String) {
        if (remote == null) return
        resetIfAccountChanged(userId)
        pushPending()
        pullAndReconcile(userId)
    }

    private suspend fun currentUserId(): String? =
        authRepository.currentSession()?.userId?.takeIf { it.isNotBlank() }

    private suspend fun pushRow(dayOfWeek: Int, figureId: Long, lens: String?) {
        val remote = remote ?: return
        val userId = currentUserId() ?: return
        val serverId = figureDao.getById(figureId)?.serverId ?: return
        try {
            remote.push(userId, dayOfWeek, serverId, lens)
            dao.markSynced(dayOfWeek)
        } catch (e: Exception) {
            // stays unsynced — retried by the next syncWithRemote pass
        }
    }

    private suspend fun pushDelete(dayOfWeek: Int) {
        val remote = remote ?: return
        val userId = currentUserId() ?: return
        try {
            remote.delete(userId, dayOfWeek)
            dao.purge(dayOfWeek)
        } catch (e: Exception) {
            // stays pendingDelete — retried by the next syncWithRemote pass
        }
    }

    private suspend fun pushPending() {
        for (row in dao.getPendingSync()) {
            if (row.pendingDelete) pushDelete(row.dayOfWeek) else pushRow(row.dayOfWeek, row.figureId, row.lens)
        }
    }

    private suspend fun resetIfAccountChanged(userId: String) {
        val meta = syncMetaDao.get()
        val previousUserId = meta?.lastDayAssignmentSyncUserId
        if (previousUserId == userId) return
        // Only wipe when a *different* account previously synced on this device — a null
        // previousUserId means this is the first sync ever, so any local pre-sync edits stay.
        if (previousUserId != null) dao.clearAll()
        syncMetaDao.upsert((meta ?: SyncMetaEntity()).copy(lastDayAssignmentSyncUserId = userId))
    }

    private suspend fun pullAndReconcile(userId: String) {
        val remote = remote ?: return
        val remoteRows = remote.fetchAll(userId)

        if (remoteRows.isEmpty() && dao.countAll() == 0) {
            seedDefaultsIfEmpty()
            pushPending()
            return
        }

        remoteRows.forEach { applyRemoteRow(it) }
        purgeMissingFromRemote(remoteRows.map { it.dayOfWeek }.toSet())
    }

    private suspend fun applyRemoteRow(row: DayAssignmentRow) {
        val figure = figureDao.getByServerId(row.figureServerId) ?: return
        val local = dao.getRawByDayOfWeek(row.dayOfWeek)
        // A local row that's pending push/delete wins for now — it'll be pushed on the next pass.
        if (local != null && (!local.synced || local.pendingDelete)) return
        dao.upsert(
            DayAssignmentEntity(
                dayOfWeek = row.dayOfWeek,
                figureId = figure.id,
                lens = row.lens,
                synced = true,
                pendingDelete = false,
            )
        )
    }

    private suspend fun purgeMissingFromRemote(remoteDays: Set<Int>) {
        for (dayOfWeek in 0..MAX_DAY_OF_WEEK) {
            if (dayOfWeek in remoteDays) continue
            val local = dao.getByDayOfWeek(dayOfWeek) ?: continue
            if (local.synced) dao.purge(dayOfWeek)
        }
    }

    companion object {
        private const val MAX_DAY_OF_WEEK = 6

        val FALLBACK_DEFAULTS = listOf(
            0 to "Augustine of Hippo",
            1 to "Julian of Norwich",
            2 to "Martin Luther",
            3 to "Brother Lawrence",
            4 to "Corrie ten Boom",
            5 to "C.S. Lewis",
            6 to "Mother Teresa",
        )
    }
}
