package com.sly.xcloud.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settingsFlow: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateStartUrl(value: String) {
        val trimmed = value.trim()
        val url = when {
            trimmed.isEmpty() -> AppSettings.DEFAULT_START_URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
        update { it.copy(startUrl = url) }
    }

    fun setDesktopMode(enabled: Boolean) {
        update { it.copy(desktopMode = enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        update { it.copy(keepScreenOn = enabled) }
    }

    fun setScriptsEnabled(enabled: Boolean) {
        update { it.copy(scriptsEnabled = enabled) }
    }

    fun setImmersiveMode(enabled: Boolean) {
        update { it.copy(immersiveMode = enabled) }
    }

    fun setCustomCssEnabled(enabled: Boolean) {
        update { it.copy(customCssEnabled = enabled) }
    }

    fun updateCustomCss(value: String) {
        update { it.copy(customCss = value) }
    }

    private fun update(block: (AppSettings) -> AppSettings) {
        val updated = block(_settings.value)
        _settings.value = updated
        save(updated)
    }

    private fun load(): AppSettings {
        return AppSettings(
            startUrl = prefs.getString(KEY_START_URL, AppSettings.DEFAULT_START_URL)
                ?: AppSettings.DEFAULT_START_URL,
            desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            scriptsEnabled = prefs.getBoolean(KEY_SCRIPTS_ENABLED, true),
            immersiveMode = prefs.getBoolean(KEY_IMMERSIVE_MODE, false),
            customCssEnabled = prefs.getBoolean(KEY_CUSTOM_CSS_ENABLED, false),
            customCss = prefs.getString(KEY_CUSTOM_CSS, "") ?: ""
        )
    }

    private fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_START_URL, settings.startUrl)
            .putBoolean(KEY_DESKTOP_MODE, settings.desktopMode)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_SCRIPTS_ENABLED, settings.scriptsEnabled)
            .putBoolean(KEY_IMMERSIVE_MODE, settings.immersiveMode)
            .putBoolean(KEY_CUSTOM_CSS_ENABLED, settings.customCssEnabled)
            .putString(KEY_CUSTOM_CSS, settings.customCss)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_START_URL = "start_url"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SCRIPTS_ENABLED = "scripts_enabled"
        private const val KEY_IMMERSIVE_MODE = "immersive_mode"
        private const val KEY_CUSTOM_CSS_ENABLED = "custom_css_enabled"
        private const val KEY_CUSTOM_CSS = "custom_css"
    }
}
