package com.example.photouploaderapp.telegrambot

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.photouploaderapp.configs.Folder
import com.example.photouploaderapp.configs.ServiceController
import com.example.photouploaderapp.configs.SettingsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScanWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val serviceController = ServiceController(applicationContext, settingsManager)

        val isPeriodic = inputData.getBoolean("is_periodic", false)

        val foldersPrefs = applicationContext.getSharedPreferences("Folders", Context.MODE_PRIVATE)
        val foldersJson = foldersPrefs.getString("folders", null)

        if (foldersJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<Folder>>() {}.type
            val folders: List<Folder> = gson.fromJson(foldersJson, type)

            val foldersToSync = folders.filter { it.isSyncing }
            if (foldersToSync.isNotEmpty()) {
                serviceController.scanFolders(foldersToSync, isPeriodic)
            }
        }

        return Result.success()
    }
}
