package com.mediasage.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeadlineCategoryPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val selectedCategory: Flow<String> = dataStore.data.map { prefs ->
        prefs[SELECTED_CATEGORY_KEY] ?: DEFAULT_CATEGORY
    }

    suspend fun selectCategory(category: String) {
        dataStore.edit { prefs -> prefs[SELECTED_CATEGORY_KEY] = category }
    }

    companion object {
        private val SELECTED_CATEGORY_KEY = stringPreferencesKey("selected_headline_category")
        private const val DEFAULT_CATEGORY = "world"
        const val FILE_NAME = "headline_category.preferences_pb"
    }
}
