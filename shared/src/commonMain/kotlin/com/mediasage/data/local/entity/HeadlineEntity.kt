package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "headlines")
data class HeadlineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val url: String,
    val imageUrl: String? = null,
    val publishedAt: Long,
    val fetchedAt: Long,
    val snippet: String? = null,
    val category: String = "",
    val isRead: Boolean = false
)
