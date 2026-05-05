package com.mediasage.server.repository

import com.mediasage.server.db.FigureTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class FiguresResponse(
    val syncedAt: Long,
    val figures: List<FigureDto>
)

@Serializable
data class FigureDto(
    val id: Long,
    val name: String,
    val category: String,
    val century: String,
    val role: String,
    val lifespan: String,
    val bio: String,
    val themes: String,
    val portraitUrl: String?,
    val isEnabled: Boolean,
    val updatedAt: Long = 0
)

class FigureRepository(private val baseUrl: String) {

    private fun resolveUrl(rawUrl: String?) =
        if (rawUrl?.startsWith("/") == true) "$baseUrl$rawUrl" else rawUrl

    suspend fun getPortraitUrl(figureName: String): String? = withContext(Dispatchers.IO) {
        transaction {
            val rawUrl = FigureTable.selectAll()
                .where { FigureTable.name eq figureName }
                .singleOrNull()?.get(FigureTable.portraitUrl)
            resolveUrl(rawUrl)
        }
    }

    suspend fun getAllEnabled(since: Long? = null): List<FigureDto> = withContext(Dispatchers.IO) {
        transaction {
            val query = FigureTable.selectAll().where {
                if (since != null) {
                    (FigureTable.isEnabled eq true) and (FigureTable.updatedAt greater since)
                } else {
                    FigureTable.isEnabled eq true
                }
            }
            query.map { row ->
                FigureDto(
                    id = row[FigureTable.id],
                    name = row[FigureTable.name],
                    category = row[FigureTable.category],
                    century = row[FigureTable.century],
                    role = row[FigureTable.role],
                    lifespan = row[FigureTable.lifespan],
                    bio = row[FigureTable.bio],
                    themes = row[FigureTable.themes],
                    portraitUrl = resolveUrl(row[FigureTable.portraitUrl]),
                    isEnabled = row[FigureTable.isEnabled],
                    updatedAt = row[FigureTable.updatedAt]
                )
            }
        }
    }
}
