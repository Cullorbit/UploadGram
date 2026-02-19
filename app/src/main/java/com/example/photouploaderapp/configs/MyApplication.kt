package com.example.photouploaderapp.configs

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    var folderAdapter: FolderAdapter? = null

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}