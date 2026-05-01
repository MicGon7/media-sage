package com.mediasage.server.repository

import com.mediasage.server.db.FigureTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

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
    val isEnabled: Boolean
)

class FigureRepository {

    suspend fun getAllEnabled(): List<FigureDto> = withContext(Dispatchers.IO) {
        transaction {
            FigureTable.selectAll()
                .where { FigureTable.isEnabled eq true }
                .map { row ->
                    FigureDto(
                        id = row[FigureTable.id],
                        name = row[FigureTable.name],
                        category = row[FigureTable.category],
                        century = row[FigureTable.century],
                        role = row[FigureTable.role],
                        lifespan = row[FigureTable.lifespan],
                        bio = row[FigureTable.bio],
                        themes = row[FigureTable.themes],
                        portraitUrl = row[FigureTable.portraitUrl],
                        isEnabled = row[FigureTable.isEnabled]
                    )
                }
        }
    }
}
