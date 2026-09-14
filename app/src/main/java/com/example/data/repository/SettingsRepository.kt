package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val assistantName: String = "Aura",
    val voiceOutputEnabled: Boolean = true,
    val autoSpeakResponses: Boolean = true,
    val language: String = "en", // "en", "hi", "hinglish"
    val themeMode: String = "dark", // "dark", "light", "system"
    val notificationsEnabled: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_assistant_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            assistantName = prefs.getString("assistant_name", "Aura") ?: "Aura",
            voiceOutputEnabled = prefs.getBoolean("voice_output", true),
            autoSpeakResponses = prefs.getBoolean("auto_speak", true),
            language = prefs.getString("language", "en") ?: "en",
            themeMode = prefs.getString("theme_mode", "dark") ?: "dark",
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        )
    }

    fun updateAssistantName(name: String) {
        prefs.edit().putString("assistant_name", name).apply()
        _settings.value = _settings.value.copy(assistantName = name)
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("voice_output", enabled).apply()
        _settings.value = _settings.value.copy(voiceOutputEnabled = enabled)
    }

    fun setAutoSpeakResponses(enabled: Boolean) {
        prefs.edit().putBoolean("auto_speak", enabled).apply()
        _settings.value = _settings.value.copy(autoSpeakResponses = enabled)
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).apply()
        _settings.value = _settings.value.copy(language = lang)
    }

    fun setThemeMode(theme: String) {
        prefs.edit().putString("theme_mode", theme).apply()
        _settings.value = _settings.value.copy(themeMode = theme)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }
}
