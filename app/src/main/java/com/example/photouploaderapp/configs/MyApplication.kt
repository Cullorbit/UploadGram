package com.example.photouploaderapp.configs

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    var folderAdapter: FolderAdapter? = null

    override fun onCreate() {
        super.onCreate()
        val settingsManager = SettingsManager(this)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settingsManager.themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}