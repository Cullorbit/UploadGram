package com.example.photouploaderapp.configs

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MenuItem
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.DialogGenericInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class NavigationHandler(
    private val activity: MainActivity,
    private val settingsManager: SettingsManager,
    private val uiUpdater: UIUpdater
) {

    fun handleNavigationItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.menu_select_bot_token -> {
                showInputDialog(
                    title = activity.getString(R.string.set_bot_token),
                    hint = activity.getString(R.string.enter_bot_token),
                    key = "KEY_BOT_TOKEN",
                    currentValue = settingsManager.botToken
                )
                true
            }
            R.id.menu_select_chat -> {
                showInputDialog(
                    title = activity.getString(R.string.set_chat_id),
                    hint = activity.getString(R.string.enter_chat_id),
                    key = "KEY_CHAT",
                    currentValue = settingsManager.chatId
                )
                true
            }
            R.id.menu_sync_options -> {
                showSyncOptionsDialog()
                true
            }
            R.id.menu_cache_limit -> {
                activity.showCacheLimitDialog()
                true
            }
            R.id.menu_reset_settings -> {
                resetSettings()
                true
            }
            R.id.menu_sync_interval_set -> {
                activity.showSyncIntervalDialog()
                true
            }
            R.id.menu_proxy_url -> {
                showProxyUrlDialog()
                true
            }
            R.id.menu_manual -> {
                openManual()
                true
            }
            R.id.menu_app_theme -> {
                showThemeDialog()
                true
            }
            else -> false
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            activity.getString(R.string.theme_system),
            activity.getString(R.string.theme_light),
            activity.getString(R.string.theme_dark)
        )
        
        val themeValues = arrayOf(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        )

        val currentTheme = settingsManager.themeMode
        val checkedItem = themeValues.indexOf(currentTheme).coerceAtLeast(0)

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.app_theme))
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedMode = themeValues[which]
                if (selectedMode != settingsManager.themeMode) {
                    settingsManager.themeMode = selectedMode
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(selectedMode)
                    activity.recreate() // Пересоздаем активность для применения темы
                }
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun showInputDialog(title: String, hint: String, key: String, currentValue: String?) {
        val binding = DialogGenericInputBinding.inflate(LayoutInflater.from(activity))
        binding.textInputLayout.hint = hint
        binding.editTextField.setText(currentValue)

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(activity.getString(R.string.save)) { dialog, _ ->
                val inputText = binding.editTextField.text.toString().trim()
                if (inputText != (currentValue ?: "")) {
                    settingsManager.saveSetting(key, inputText)
                    uiUpdater.updateSettingsDisplay()
                    activity.showToast(activity.getString(R.string.setting_saved))
                    activity.stopServiceIfNeeded()
                }
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun resetSettings() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.reset_app_settings))
            .setMessage(activity.getString(R.string.reset_settings_confirmation))
            .setPositiveButton(activity.getString(R.string.yes)) { dialog, _ ->
                settingsManager.clearSettings()
                uiUpdater.updateSettingsDisplay()
                activity.showToast(activity.getString(R.string.settings_reset))
                activity.stopServiceIfNeeded()
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSyncOptionsDialog() {
        val options = arrayOf(activity.getString(R.string.wifi_only), activity.getString(R.string.wifi_and_mobile_data))
        val currentOption = settingsManager.syncOption
        var tempOption = currentOption
        val checkedItem = when (currentOption) {
            "wifi_only" -> 0
            else -> 1
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.synchronization))
            .setSingleChoiceItems(options, checkedItem) { _, which ->
                tempOption = if (which == 0) "wifi_only" else "wifi_and_mobile"
            }
            .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                if (settingsManager.syncOption != tempOption) {
                    settingsManager.syncOption = tempOption
                    uiUpdater.updateSettingsDisplay()
                    activity.stopServiceIfNeeded()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun showProxyUrlDialog() {
        val binding = DialogGenericInputBinding.inflate(LayoutInflater.from(activity))
        binding.textInputLayout.hint = activity.getString(R.string.enter_proxy_url)
        
        // Показываем описание сверху над полем ввода
        binding.tvDescription.visibility = android.view.View.VISIBLE
        binding.tvDescription.text = activity.getString(R.string.proxy_url_description)

        // Показываем URL только если он не совпадает со значением по умолчанию
        val currentUrl = settingsManager.proxyUrl
        val defaultUrl = "https://telegram-bot-api-latest-wuhf.onrender.com"
        
        if (currentUrl != defaultUrl) {
            binding.editTextField.setText(currentUrl)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.set_proxy_url))
            .setView(binding.root)
            .setNeutralButton(activity.getString(R.string.default_button)) { _, _ ->
                settingsManager.proxyUrl = defaultUrl
                uiUpdater.updateSettingsDisplay()
                activity.showToast(activity.getString(R.string.proxy_url_default_toast))
                activity.stopServiceIfNeeded()
            }
            .setPositiveButton(activity.getString(R.string.save), null) // Устанавливаем null, чтобы переопределить поведение ниже
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
            .apply {
                // Переопределяем кнопку "Сохранить", чтобы диалог не закрывался при ошибке валидации
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val inputText = binding.editTextField.text.toString().trim()
                    
                    if (inputText.isEmpty()) {
                        binding.textInputLayout.error = activity.getString(R.string.field_cannot_be_empty)
                    } else if (!inputText.startsWith("http://") && !inputText.startsWith("https://")) {
                        binding.textInputLayout.error = activity.getString(R.string.invalid_url_format)
                    } else {
                        settingsManager.proxyUrl = inputText
                        uiUpdater.updateSettingsDisplay()
                        activity.showToast(activity.getString(R.string.setting_saved))
                        activity.stopServiceIfNeeded()
                        dismiss()
                    }
                }
            }
    }

    private fun openManual() {
        val url = "https://github.com/Cullorbit/UploadGram/blob/master/USAGE.md"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        activity.startActivity(intent)
    }
}
