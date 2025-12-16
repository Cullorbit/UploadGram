package com.example.photouploaderapp.configs

import android.content.Context
import androidx.core.content.edit

class SettingsManager(private val context: Context) {

    internal val sharedPreferences by lazy {
        context.getSharedPreferences("TelegramSettings", Context.MODE_PRIVATE)
    }

    var selectedMediaType: String
        get() = sharedPreferences.getString("KEY_MEDIA_TYPE", "Все") ?: "Все"
        set(value) {
            sharedPreferences.edit().putString("KEY_MEDIA_TYPE", value).apply()
        }

    var syncInterval: Long
        get() = sharedPreferences.getLong("sync_interval", 15 * 60 * 1000) // 15 минут по умолчанию
        set(value) = sharedPreferences.edit { putLong("sync_interval", value) }

    var botToken: String?
        get() = sharedPreferences.getString("KEY_BOT_TOKEN", null)
        set(value) = sharedPreferences.edit { putString("KEY_BOT_TOKEN", value) }

    var chatId: String?
        get() = sharedPreferences.getString("KEY_CHAT", null)
        set(value) = sharedPreferences.edit { putString("KEY_CHAT", value) }

    var syncOption: String
        get() = sharedPreferences.getString("sync_option", "wifi_and_mobile") ?: "wifi_and_mobile"
        set(value) = sharedPreferences.edit { putString("sync_option", value) }

    fun clearSettings() {
        sharedPreferences.edit { clear() }
    }

    fun saveSetting(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }
}
