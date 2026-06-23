package com.example.photouploaderapp.configs

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_PROXY_URL = "https://telegram-bot-api-latest-wuhf.onrender.com"
        private val PART1 = "8967979974"
        private val PART2 = "AAGC2QoM"
        private val PART3 = "8rYHihl8JTC12"
        private val PART4 = "jfZ7SijAN6i_m8"
        
        val INTERNAL_AUTH_BOT_TOKEN: String
            get() = "$PART1:$PART2$PART3$PART4"
    }

    var botToken: String
        get() = prefs.getString("bot_token", "")?.ifBlank { INTERNAL_AUTH_BOT_TOKEN } ?: INTERNAL_AUTH_BOT_TOKEN
        set(value) = prefs.edit().putString("bot_token", value).apply()

    fun getRawBotToken(): String? = prefs.getString("bot_token", null)

    var chatId: String?
        get() = prefs.getString("chat_id", null)
        set(value) = prefs.edit().putString("chat_id", value).apply()

    var chatTitle: String?
        get() = prefs.getString("chat_title", null)
        set(value) = prefs.edit().putString("chat_title", value).apply()

    var proxyUrl: String
        get() = prefs.getString("proxy_url", DEFAULT_PROXY_URL).let {
            if (it.isNullOrBlank()) DEFAULT_PROXY_URL else it
        }
        set(value) = prefs.edit().putString("proxy_url", value).apply()

    fun getRawProxyUrl(): String? = prefs.getString("proxy_url", null)

    var syncIntervalMinutes: Int
        get() {
            return try {
                prefs.getString("sync_interval", "15")?.toInt() ?: 15
            } catch (e: Exception) {
                15
            }
        }
        set(value) = prefs.edit().putString("sync_interval", value.toString()).apply()

    var isWifiOnly: Boolean
        get() = prefs.getString("sync_option", "wifi_only") == "wifi_only"
        set(value) = prefs.edit().putString("sync_option", if (value) "wifi_only" else "wifi_and_mobile").apply()

    var themeMode: Int
        get() {
            return try {
                prefs.getString("theme_mode", "-1")?.toInt() ?: -1
            } catch (e: Exception) {
                -1
            }
        }
        set(value) = prefs.edit().putString("theme_mode", value.toString()).apply()

    var cacheLimit: Long
        get() {
            return try {
                prefs.getString("cache_limit", "0")?.toLong() ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        set(value) = prefs.edit().putString("cache_limit", value.toString()).apply()

    var selectedMediaType: String?
        get() = prefs.getString("selected_media_type", null)
        set(value) = prefs.edit().putString("selected_media_type", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("is_service_running", false)
        set(value) = prefs.edit().putBoolean("is_service_running", value).apply()

    fun clearSettings() {
        prefs.edit().clear().apply()
        context.getSharedPreferences("Folders", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("AppLog", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
