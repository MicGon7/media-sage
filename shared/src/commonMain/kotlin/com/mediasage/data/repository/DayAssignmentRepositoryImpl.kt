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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isResolved = MutableStateFlow(false)
    override val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

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
        pushRow(dayOfWeek)
    }

    override suspend fun clear(dayOfWeek: Int) {
        dao.markPendingDelete(dayOfWeek)
        pushDelete(dayOfWeek)
    }

    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? =
        dailyReflectionRepository.getLockedFigureId(epochDay) ?: dao.getByDayOfWeek(dayOfWeek)?.figureId

    override suspend fun resolve(userId: String?) {
        // Flips back to false for the duration of *every* resolve pass, not just the first —
        // otherwise a later correction (e.g. the real signed-in schedule replacing fallback
        // defaults seeded moments earlier on a fresh install) mutates the data a live collector
        // is already showing with no signal to prefer a loading state over the stale content.
        _isResolved.value = false
        try {
            // A signed-out resolve() has no account to push these rows to — mark them
            // already-synced so they're pure local placeholder content, never a "pending
            // edit" pushPending() could later mistake for something to push (and overwrite a
            // real remote schedule with) once a genuine sign-in follows moments later, which
            // is exactly what happens on a fresh install: authState passes through
            // Unauthenticated before the user signs back in.
            if (userId != null) syncWithRemote(userId) else seedDefaultsIfEmpty(markAsSynced = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Failure is non-fatal — retried on next launch/sign-in
        } finally {
            _isResolved.value = true
        }
    }

    private suspend fun seedDefaultsIfEmpty(markAsSynced: Boolean = false) {
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
            dao.upsert(DayAssignmentEntity(dayOfWeek = dayOrdinal, figureId = figure.id, synced = markAsSynced))
        }
    }

    private suspend fun syncWithRemote(userId: String) {
        if (remote == null) return
        resetIfAccountChanged(userId)
        pushPending()
        pullAndReconcile(userId)
    }

    private suspend fun currentUserId(): String? =
        authRepository.currentSession()?.userId?.takeIf { it.isNotBlank() }

    private suspend fun pushRow(dayOfWeek: Int) {
        val remote = remote ?: return
        val userId = currentUserId() ?: return
        // Re-reads Room right before pushing rather than trusting a caller-supplied
        // figureId/lens — pushPending()'s snapshot can otherwise go stale if a concurrent
        // assign() lands between the snapshot read and this push, re-pushing an old value
        // with a fresh timestamp and clobbering the newer one on the server.
        val row = dao.getRawByDayOfWeek(dayOfWeek)?.takeUnless { it.synced || it.pendingDelete } ?: return
        val serverId = figureDao.getById(row.figureId)?.serverId ?: return
        try {
            remote.push(userId, dayOfWeek, serverId, row.lens)
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
            if (row.pendingDelete) pushDelete(row.dayOfWeek) else pushRow(row.dayOfWeek)
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
