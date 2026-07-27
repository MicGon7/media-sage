package com.mediasage.data.repository

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.SyncMetaEntity
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class EncouragementRepositoryImpl(
    private val api: MediaSageApi,
    private val encouragementDao: EncouragementDao,
    private val figureDao: FigureDao,
    private val remote: SavedInsightRemoteDataSource?,
    private val syncMetaDao: SyncMetaDao,
    private val authRepository: AuthRepository,
) : EncouragementRepository {

    private val _isResolved = MutableStateFlow(false)
    override val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?
    ): Encouragement {
        articleUrl?.let { url ->
            encouragementDao.getByArticleUrl(url)?.let { return it.toDomain() }
        }

        val encouragement = api.encourage(
            EncourageRequestDto(
                headlineTitle = headlineTitle,
                articleUrl = articleUrl,
                articleSnippet = articleSnippet
            )
        ).toDomain()

        articleUrl?.let {
            val figureId = figureDao.getByName(encouragement.figureName)?.id
            encouragementDao.insert(
                encouragement.toEntity(it, headlineTitle, headlineSource, headlineImageUrl, currentTimeMillis(), figureId)
            )
        }
        return encouragement
    }

    override fun observeAll(): Flow<List<Encouragement>> =
        encouragementDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeBookmarked(): Flow<List<Encouragement>> =
        encouragementDao.observeBookmarked().map { entities -> entities.map { it.toDomain() } }

    override fun observeCountByFigureName(): Flow<Map<String, Int>> =
        encouragementDao.observeCountByFigureName()

    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> =
        encouragementDao.observeByFigureId(figureId).map { entities -> entities.map { it.toDomain() } }

    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> =
        encouragementDao.observeBookmarkState(articleUrl)

    override suspend fun toggleBookmark(articleUrl: String) {
        encouragementDao.toggleBookmark(articleUrl)
        val row = encouragementDao.getByArticleUrl(articleUrl) ?: return
        if (row.pendingDelete) pushDelete(articleUrl) else pushRow(articleUrl)
    }

    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> {
        val start = epochDay * MS_PER_DAY
        return encouragementDao.observeByDateRange(start, start + MS_PER_DAY)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeActiveEpochDays(): Flow<Set<Long>> =
        encouragementDao.observeActiveEpochDays().map { it.toSet() }

    override suspend fun resolve(userId: String?) {
        // Flips back to false for the duration of every resolve pass — see the matching
        // comment in DayAssignmentRepositoryImpl.resolve() for why a one-shot latch isn't enough.
        _isResolved.value = false
        try {
            if (userId != null) syncWithRemote(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Failure is non-fatal — retried on next launch/sign-in
        } finally {
            _isResolved.value = true
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

    private suspend fun pushRow(articleUrl: String) {
        val remote = remote ?: return
        val userId = currentUserId() ?: return
        val row = encouragementDao.getByArticleUrl(articleUrl)?.takeIf { it.bookmarked && !it.synced } ?: return
        val serverId = row.figureId?.let { figureDao.getById(it)?.serverId } ?: return
        try {
            remote.push(row.toSavedInsightRow(userId, serverId))
            encouragementDao.markSynced(articleUrl)
        } catch (e: Exception) {
            // stays unsynced — retried by the next syncWithRemote pass
        }
    }

    private suspend fun pushDelete(articleUrl: String) {
        val remote = remote ?: return
        val userId = currentUserId() ?: return
        try {
            remote.delete(userId, articleUrl)
            encouragementDao.clearBookmarkState(articleUrl)
        } catch (e: Exception) {
            // stays pendingDelete — retried by the next syncWithRemote pass
        }
    }

    private suspend fun pushPending() {
        for (row in encouragementDao.getPendingSync()) {
            if (row.pendingDelete) pushDelete(row.articleUrl) else pushRow(row.articleUrl)
        }
    }

    private suspend fun resetIfAccountChanged(userId: String) {
        val meta = syncMetaDao.get()
        val previousUserId = meta?.lastSavedInsightSyncUserId
        if (previousUserId == userId) return
        // Only reset when a *different* account previously synced on this device — a null
        // previousUserId means this is the first sync ever, so any local pre-sync bookmarks stay.
        // Bookmark state only, never clearAll() — this table also holds shared cache content
        // that has nothing to do with the previous account's saved insights.
        if (previousUserId != null) encouragementDao.resetBookmarkStateForAccountSwitch()
        syncMetaDao.upsert((meta ?: SyncMetaEntity()).copy(lastSavedInsightSyncUserId = userId))
    }

    private suspend fun pullAndReconcile(userId: String) {
        val remote = remote ?: return
        val remoteRows = remote.fetchAll(userId)
        remoteRows.forEach { applyRemoteRow(it) }

        val remoteUrls = remoteRows.map { it.articleUrl }.toSet()
        for (articleUrl in encouragementDao.getSyncedBookmarkedArticleUrls()) {
            if (articleUrl !in remoteUrls) encouragementDao.clearBookmarkState(articleUrl)
        }
    }

    private suspend fun applyRemoteRow(row: SavedInsightRow) {
        val figure = figureDao.getByServerId(row.figureServerId) ?: return
        val local = encouragementDao.getByArticleUrl(row.articleUrl)
        // A local row that's pending push/delete wins for now — it'll be pushed on the next pass.
        if (local != null && (!local.synced || local.pendingDelete)) return
        encouragementDao.upsert(row.toEncouragementEntity(figure.id, cachedAt = local?.cachedAt ?: currentTimeMillis()))
    }
}

private fun EncouragementEntity.toSavedInsightRow(userId: String, figureServerId: Long) = SavedInsightRow(
    userId = userId,
    articleUrl = articleUrl,
    figureServerId = figureServerId,
    summary = summary,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    explanation = explanation,
    connectionThemes = if (connectionThemes.isBlank()) emptyList() else connectionThemes.split(",").map { it.trim() },
    matchTheme = matchTheme,
    tone = tone,
    figureImageUrl = figureImageUrl,
    headlineTitle = headlineTitle,
    headlineSource = headlineSource,
    headlineImageUrl = headlineImageUrl,
)

private fun SavedInsightRow.toEncouragementEntity(figureId: Long, cachedAt: Long) = EncouragementEntity(
    articleUrl = articleUrl,
    summary = summary,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    explanation = explanation,
    connectionThemes = connectionThemes.joinToString(","),
    matchTheme = matchTheme,
    tone = tone,
    figureImageUrl = figureImageUrl,
    headlineTitle = headlineTitle,
    headlineSource = headlineSource,
    headlineImageUrl = headlineImageUrl,
    cachedAt = cachedAt,
    bookmarked = true,
    figureId = figureId,
    synced = true,
    pendingDelete = false,
)

private const val MS_PER_DAY = 86_400_000L
