package com.example.photouploaderapp.configs

import android.content.Context
import android.util.Log
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
        get() = sharedPreferences.getLong("sync_interval", 60 * 60 * 1000) // 1 час по умолчанию
        set(value) = sharedPreferences.edit { putLong("sync_interval", value) }

    // Сохранение и получение Bot Token
    var botToken: String?
        get() = sharedPreferences.getString("KEY_BOT_TOKEN", null)
        set(value) = sharedPreferences.edit() { putString("KEY_BOT_TOKEN", value) }

    // Сохранение и получение Chat ID
    var chatId: String?
        get() = sharedPreferences.getString("KEY_CHAT", null)
        set(value) = sharedPreferences.edit() { putString("KEY_CHAT", value) }

    // Сохранение и получение типа медиа
    var mediaType: String?
        get() = sharedPreferences.getString("KEY_MEDIA_TYPE", "all")
        set(value) = sharedPreferences.edit() { putString("KEY_MEDIA_TYPE", value) }

    // Сохранение и получение папки синхронизации
    var syncFolder: String?
        get() = sharedPreferences.getString("KEY_SYNC_FOLDER", null)
        set(value) = sharedPreferences.edit() { putString("KEY_SYNC_FOLDER", value) }

    var syncOption: String
        get() = sharedPreferences.getString("sync_option", "wifi_and_mobile") ?: "wifi_and_mobile"
        set(value) = sharedPreferences.edit { putString("sync_option", value) }

    // Сохранение и получение номера темы
    var topic: String?
        get() {
            val topicValue = sharedPreferences.getString("KEY_TOPIC", null)
            Log.d("SettingsManager", "topic loaded: $topicValue")
            return topicValue
        }
        set(value) {
            sharedPreferences.edit() { putString("KEY_TOPIC", value) }
            Log.d("SettingsManager", "topic saved: $value")
        }

    // Включение/выключение использования темы
    var isTopicEnabled: Boolean
        get() {
            val isEnabled = sharedPreferences.getBoolean("KEY_TOPIC_ENABLED", false)
            Log.d("SettingsManager", "isTopicEnabled loaded: $isEnabled")
            return isEnabled
        }
        set(value) {
            sharedPreferences.edit() { putBoolean("KEY_TOPIC_ENABLED", value) }
            Log.d("SettingsManager", "isTopicEnabled saved: $value")
        }

    // Управление темной темой
    var isDarkTheme: Boolean
        get() = sharedPreferences.getBoolean("DARK_THEME", false)
        set(value) {
            sharedPreferences.edit() { putBoolean("DARK_THEME", value) }
            Log.d("SettingsManager", "isDarkTheme saved: $value")
        }

    // Сброс всех настроек
    fun clearSettings() {
        sharedPreferences.edit() { clear() }
    }

    fun saveSetting(key: String, value: String) {
        sharedPreferences.edit() { putString(key, value) }
    }
}
