package com.mediasage.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val rememberedEmail: Flow<String> = dataStore.data.map { prefs ->
        prefs[REMEMBERED_EMAIL_KEY] ?: ""
    }

    suspend fun setRememberedEmail(email: String) {
        dataStore.edit { it[REMEMBERED_EMAIL_KEY] = email }
    }

    suspend fun clearRememberedEmail() {
        dataStore.edit { it.remove(REMEMBERED_EMAIL_KEY) }
    }

    companion object {
        private val REMEMBERED_EMAIL_KEY = stringPreferencesKey("remembered_email")
        const val FILE_NAME = "user.preferences_pb"
    }
}
