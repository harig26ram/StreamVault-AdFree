package com.streamvault.app.presentation.ui.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import com.streamvault.app.presentation.ui.theme.AppThemes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

private val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selectedTheme = MutableStateFlow(AppThemes.first())
    val selectedTheme: StateFlow<Theme> = _selectedTheme

    init {
        scope.launch {
            context.dataStore.data.map { prefs ->
                val id = prefs[SELECTED_THEME_ID] ?: AppThemes.first().id
                AppThemes.firstOrNull { it.id == id } ?: AppThemes.first()
            }.collect { theme ->
                _selectedTheme.value = theme
            }
        }
    }

    fun setTheme(theme: Theme) {
        _selectedTheme.value = theme
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[SELECTED_THEME_ID] = theme.id
            }
        }
    }
}
