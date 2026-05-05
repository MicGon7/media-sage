package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "figures")
data class FigureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val century: String,
    val bio: String = "",
    val role: String = "",
    val lifespan: String = "",
    val themes: String = "",
    val portraitUrl: String? = null,
    val serverId: Long = 0
)
