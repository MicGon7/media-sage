package com.mediasage.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeadlineCategoryPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    /** Empty set means no filter — the default, unfiltered behavior. */
    val selectedCategories: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SELECTED_CATEGORIES_KEY] ?: emptySet()
    }

    suspend fun toggleCategory(category: String) {
        dataStore.edit { prefs ->
            val current = prefs[SELECTED_CATEGORIES_KEY] ?: emptySet()
            prefs[SELECTED_CATEGORIES_KEY] = if (category in current) current - category else current + category
        }
    }

    companion object {
        private val SELECTED_CATEGORIES_KEY = stringSetPreferencesKey("selected_headline_categories")
        const val FILE_NAME = "headline_category.preferences_pb"
    }
}
