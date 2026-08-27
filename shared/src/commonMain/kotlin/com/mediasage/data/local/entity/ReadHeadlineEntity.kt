package com.mediasage.data.local.entity

import androidx.room.Entity

// A separate per-user table, not a column on HeadlineEntity — headlines is a shared content
// cache that gets wiped and refetched independently of any account, while read state must
// survive that churn and never leak between accounts on the same device (MS-734).
@Entity(tableName = "read_headlines", primaryKeys = ["userId", "url"])
data class ReadHeadlineEntity(
    val userId: String,
    val url: String
)
