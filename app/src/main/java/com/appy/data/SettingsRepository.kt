package com.appy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.appy.ui.screens.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore extension property
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for app settings using DataStore
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val MATERIAL_YOU_KEY = booleanPreferencesKey("material_you_enabled")
    }
    
    /**
     * Flow for theme mode setting
     */
    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[THEME_MODE_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
    
    /**
     * Flow for Material You setting
     */
    val materialYouFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MATERIAL_YOU_KEY] ?: true
    }
    
    /**
     * Save theme mode
     */
    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name
        }
    }
    
    /**
     * Save Material You setting
     */
    suspend fun setMaterialYouEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MATERIAL_YOU_KEY] = enabled
        }
    }
}
