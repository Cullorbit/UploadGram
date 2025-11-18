package com.example.photouploaderapp.configs

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R

class UIUpdater(private val activity: MainActivity, private val settingsManager: SettingsManager) {

    fun updateSettingsDisplay() {
        val navigationView = activity.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
            .findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)

        val menu = navigationView.menu

        // Обновление Bot Token
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_bot_token),
            isSet = !settingsManager.botToken.isNullOrEmpty(),
            title = "Bot Token: ${if (settingsManager.botToken != null) "Установлен" else "Не установлен"}"
        )

        // Обновление Chat ID
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_chat_id),
            isSet = !settingsManager.chatId.isNullOrEmpty(),
            title = "Chat ID: ${settingsManager.chatId ?: "Не установлен"}"
        )

     /*   // Обновление типа медиа
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_media_type),
            isSet = true,
            title = "Тип медиа: ${when (settingsManager.mediaType) {
                "photo" -> "Только фото"
                "video" -> "Только видео"
                else -> "Фото и видео"
            }}"
        )

        // Обновление папки синхронизации
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_sync_folder),
            isSet = !settingsManager.syncFolder.isNullOrEmpty(),
            title = "Папка: ${if (settingsManager.syncFolder != null) "Выбрана" else "Не выбрана"}"
        )

        // Обновление номера темы
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_topic_id),
            isSet = settingsManager.isTopicEnabled && !settingsManager.topic.isNullOrEmpty(),
            title = "Тема №: ${settingsManager.topic ?: "Не установлена"}"
        )*/

        // Обновление пункта для темной темы
        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_toggle_theme),
            isSet = settingsManager.isDarkTheme,
            title = "Темная тема: ${if (settingsManager.isDarkTheme) "Включена" else "Выключена"}"
        )

        /*updateMenuItem(
            menuItem = menu.findItem(R.id.menu_sync_options),
            isSet = true,
            title = "Синхронизация: ${getSyncOptionTitle()}"
        )*/

            /*updateMenuItem(
            menuItem = menu.findItem(R.id.menu_sync_interval),
            isSet = true,
            title = "Интервал: ${settingsManager.syncInterval} минут"
        )*/
    }

    private fun updateMenuItem(menuItem: android.view.MenuItem?, isSet: Boolean, title: String) {
        Log.d("UIUpdater", "updateMenuItem called with isSet: $isSet, title: $title")
        menuItem?.apply {
            this.title = title
            icon = getIconDrawable(if (isSet) R.drawable.ic_check_green else R.drawable.ic_check_gray)
        }
    }

    private fun getIconDrawable(resId: Int): Drawable? {
        return ContextCompat.getDrawable(activity, resId)
    }

    fun updateServiceButtons(isServiceActive: Boolean) {
        Log.d("UIUpdater", "updateServiceButtons called with isServiceActive: $isServiceActive")
        activity.btnStartService.isEnabled = !isServiceActive
        activity.btnStopService.isEnabled = isServiceActive
    }
    private fun getSyncOptionTitle(): String {
        val option = settingsManager.syncOption
        return when (option) {
            "wifi_only" -> "Только по WiFi"
            else -> "WiFi и Мобильные данные"
        }
    }
}
