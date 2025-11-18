package com.example.photouploaderapp.configs

import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R

class NavigationHandler(
    private val activity: MainActivity,
    private val showMediaTypeDialog: () -> Unit,
    private val settingsManager: SettingsManager,
    private val uiUpdater: UIUpdater
) {
    //Общий стек навигации приложения
    fun handleNavigationItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_select_bot_token -> {
                showInputDialog(
                    title = "Задать Bot Token",
                    hint = "Введите Bot Token",
                    key = "KEY_BOT_TOKEN"
                )
                true
            }
            R.id.menu_select_chat -> {
                showInputDialog(
                    title = "Задать Chat ID",
                    hint = "Введите Chat ID",
                    key = "KEY_CHAT"
                )
                true
            }
            R.id.menu_sync_options -> {
                showSyncOptionsDialog()
                true
            }
           /* R.id.menu_select_topic -> {
                showInputDialog(
                    title = "Задать номер темы",
                    hint = "Введите номер темы",
                    key = "KEY_TOPIC"
                )
                true
            }
            R.id.menu_select_media_type -> {
                showMediaTypeDialog()
                true
            }
            R.id.menu_select_folder -> {
                activity.folderPickerLauncher.launch(null)
                true
            }*/
            R.id.menu_reset_settings -> {
                resetSettings()
                true
            }
            R.id.menu_toggle_theme -> {
                activity.toggleTheme()
                true
            }
            R.id.menu_sync_interval -> {
                showIntervalDialog()
                true
            }
            R.id.menu_sync_interval_set -> {
                activity.showSyncIntervalDialog()
                true
            }
            else -> false
        }
    }

    //Диалог выбора интервала синхронизации
    private fun showIntervalDialog() {
        val options = arrayOf("1 минута", "10 минут", "30 минут", "60 минут")
        val currentInterval = settingsManager.syncInterval
        val checkedItem = when (currentInterval) {
            60 * 1000L -> 0 // 1 минута в миллисекундах
            600 * 1000L -> 1 // 10 минут в миллисекундах
            1800 * 1000L -> 2 // 30 минут в миллисекундах
            3600 * 1000L -> 3 // 60 минут в миллисекундах
            else -> 0
        }

        AlertDialog.Builder(activity)
            .setTitle("Интервал синхронизации")
            .setSingleChoiceItems(options, checkedItem) { _, which ->
                when (which) {
                    0 -> settingsManager.syncInterval = 60 * 1000L
                    1 -> settingsManager.syncInterval = 600 * 1000L
                    2 -> settingsManager.syncInterval = 1800 * 1000L
                    3 -> settingsManager.syncInterval = 3600 * 1000L
                }
                uiUpdater.updateSettingsDisplay()
            }
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
    //Диалог выбора номера темы
    private fun showInputDialog(title: String, hint: String, key: String) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(title)

        val inputField = androidx.appcompat.widget.AppCompatEditText(activity).apply {
            this.hint = hint
        }

        builder.setView(inputField)
        builder.setPositiveButton("OK") { dialog, _ ->
            val inputText = inputField.text.toString().trim()
            if (inputText.isNotEmpty()) {
                settingsManager.saveSetting(key, inputText)
                if (key == "KEY_TOPIC") {
                    settingsManager.isTopicEnabled = true
                }
                uiUpdater.updateSettingsDisplay()
                activity.showToast("Настройка сохранена.")
            } else {
                activity.showToast("Поле не может быть пустым.")
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    //Сброс настроек приложения
    private fun resetSettings() {
        AlertDialog.Builder(activity)
            .setTitle("Сброс настроек")
            .setMessage("Вы уверены, что хотите сбросить все настройки приложения?")
            .setPositiveButton("Да") { dialog, _ ->
                settingsManager.clearSettings()
                uiUpdater.updateSettingsDisplay()
                activity.showToast("Настройки сброшены")
                uiUpdater.updateServiceButtons(false)
                dialog.dismiss()
            }
            .setNegativeButton("Нет") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    private fun showSyncOptionsDialog() {
        val options = arrayOf("Только по WiFi", "WiFi и Мобильные данные")
        val currentOption = settingsManager.syncOption
        val checkedItem = when (currentOption) {
            "wifi_only" -> 0
            else -> 1
        }

        AlertDialog.Builder(activity)
            .setTitle("Синхронизация:")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                when (which) {
                    0 -> settingsManager.syncOption = "wifi_only"
                    else -> settingsManager.syncOption = "wifi_and_mobile"
                }
                uiUpdater.updateSettingsDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
}
