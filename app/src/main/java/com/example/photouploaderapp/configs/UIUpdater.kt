package com.example.photouploaderapp.configs

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R

class UIUpdater(private val activity: MainActivity, private val settingsManager: SettingsManager) {

    fun updateSettingsDisplay() {
        val navigationView = activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val menu = navigationView.menu

        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_bot_token),
            isSet = !settingsManager.botToken.isNullOrEmpty(),
            title = if (settingsManager.botToken != null) activity.getString(R.string.bot_token_set) else activity.getString(R.string.bot_token_not_set)
        )

        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_chat_id),
            isSet = !settingsManager.chatId.isNullOrEmpty(),
            title = if (!settingsManager.chatId.isNullOrEmpty()) activity.getString(R.string.chat_id_set, settingsManager.chatId) else activity.getString(R.string.chat_id_not_set)
        )

        updateMenuItem(
            menuItem = menu.findItem(R.id.menu_toggle_theme),
            isSet = settingsManager.isDarkTheme,
            title = if (settingsManager.isDarkTheme) activity.getString(R.string.dark_theme_on) else activity.getString(R.string.dark_theme_off)
        )
    }

    private fun updateMenuItem(menuItem: android.view.MenuItem?, isSet: Boolean, title: String) {
        menuItem?.apply {
            this.title = title
            icon = getIconDrawable(if (isSet) R.drawable.ic_check_green else R.drawable.ic_check_gray)
        }
    }

    private fun getIconDrawable(resId: Int): Drawable? {
        return ContextCompat.getDrawable(activity, resId)
    }
}
