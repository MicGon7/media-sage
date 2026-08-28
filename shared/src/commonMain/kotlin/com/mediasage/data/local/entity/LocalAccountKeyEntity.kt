package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// The device's cached copy of the signed-in account's shared reflection-note key (MS-740).
// wrappedKeyBase64 is the raw key, Base64-encoded then wrapped by this device's own non-exportable
// ReflectionNoteCipher key — this table never holds the raw key material in the clear.
@Entity(tableName = "local_account_key")
data class LocalAccountKeyEntity(
    @PrimaryKey val userId: String,
    val wrappedKeyBase64: String,
)
