package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "figures")
data class FigureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,    // theologian, mystic, modern, biblical
    val century: String,     // e.g. "4th", "16th", "20th"
    val description: String = ""
)
