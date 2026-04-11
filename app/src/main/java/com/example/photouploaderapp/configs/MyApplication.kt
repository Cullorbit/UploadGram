package com.example.photouploaderapp.configs

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    var folderAdapter: FolderAdapter? = null

    companion object {
        private lateinit var instance: MyApplication
        fun getContext(): Context = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val settingsManager = SettingsManager(this)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settingsManager.themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}