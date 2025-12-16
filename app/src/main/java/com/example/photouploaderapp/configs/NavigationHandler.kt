package com.example.photouploaderapp.configs

import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import android.widget.EditText
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

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
            R.id.menu_reset_settings -> {
                resetSettings()
                true
            }
            R.id.menu_sync_interval_set -> {
                activity.showSyncIntervalDialog()
                true
            }
            R.id.menu_manual -> {
                openManual()
                true
            }
            else -> false
        }
    }

    private fun showInputDialog(title: String, hint: String, key: String, currentValue: String?) {
        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle(title)

        val textInputLayout = TextInputLayout(activity).apply {
            setPadding(
                (19 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (19 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            this.hint = hint
        }
        val inputField = EditText(activity).apply {
            setText(currentValue)
        }
        textInputLayout.addView(inputField)

        builder.setView(textInputLayout)

        builder.setPositiveButton(activity.getString(R.string.save)) { dialog, _ ->
            val inputText = inputField.text.toString().trim()
            if (inputText != (currentValue ?: "")) {
                settingsManager.saveSetting(key, inputText)
                uiUpdater.updateSettingsDisplay()
                activity.showToast(activity.getString(R.string.setting_saved))
                activity.stopServiceIfNeeded()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
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
        val checkedItem = when (currentOption) {
            "wifi_only" -> 0
            else -> 1
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.synchronization))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val newOption = if (which == 0) "wifi_only" else "wifi_and_mobile"
                if (settingsManager.syncOption != newOption) {
                    settingsManager.syncOption = newOption
                    uiUpdater.updateSettingsDisplay()
                    activity.stopServiceIfNeeded()
                }
                dialog.dismiss()
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun openManual() {
        val url = "https://github.com/Cullorbit/UploadGram/blob/master/USAGE.md"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        activity.startActivity(intent)
    }
}
