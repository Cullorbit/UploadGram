package com.example.photouploaderapp.configs

import android.view.MenuItem
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R

class UIUpdater(private val activity: MainActivity, private val settingsManager: SettingsManager) {

    private val navigationView by lazy {
        (activity as? MainActivity)?.let {
            val mainActivityBinding = it.binding
            mainActivityBinding.navigationView
        }
    }

    fun updateSettingsDisplay() {
        val menu = navigationView?.menu ?: return

        val botTokenItem = menu.findItem(R.id.menu_select_bot_token)
        val botTokenStatus = if (settingsManager.botToken.isNullOrEmpty()) {
            activity.getString(R.string.bot_token_not_set)
        } else {
            activity.getString(R.string.bot_token_set)
        }
        botTokenItem?.title = "${activity.getString(R.string.set_bot_token)}\n($botTokenStatus)"

        val chatItem = menu.findItem(R.id.menu_select_chat)
        val chatIdStatus = if (settingsManager.chatId.isNullOrEmpty()) {
            activity.getString(R.string.chat_id_not_set)
        } else {
            activity.getString(R.string.chat_id_set, settingsManager.chatId)
        }
        chatItem?.title = "${activity.getString(R.string.set_chat_id)}\n($chatIdStatus)"

        val syncOptionItem = menu.findItem(R.id.menu_sync_options)
        val syncOptionText = if (settingsManager.syncOption == "wifi_only") {
            activity.getString(R.string.wifi_only)
        } else {
            activity.getString(R.string.wifi_and_mobile_data)
        }
        syncOptionItem?.title = "${activity.getString(R.string.synchronization)}\n($syncOptionText)"


        val syncIntervalItem = menu.findItem(R.id.menu_sync_interval_set)
        val intervalMinutes = settingsManager.syncInterval / (1000 * 60)
        val intervalStatus = activity.getString(R.string.sync_interval_minutes, intervalMinutes.toString())
        syncIntervalItem?.title = "${activity.getString(R.string.set_sync_interval)}\n($intervalStatus)"

    }
}
