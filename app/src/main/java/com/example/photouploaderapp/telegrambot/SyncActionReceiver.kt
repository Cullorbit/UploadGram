package com.example.photouploaderapp.telegrambot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.photouploaderapp.configs.Folder
import com.example.photouploaderapp.configs.ServiceController
import com.example.photouploaderapp.configs.SettingsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SyncActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settingsManager = SettingsManager(context)
        val serviceController = ServiceController(context, settingsManager)

        when (intent.action) {
            "com.example.photouploaderapp.ACTION_START" -> {
                val folders = loadFolders(context)
                if (folders.any { it.isSyncing }) {
                    serviceController.startService(folders)
                }
            }
            "com.example.photouploaderapp.ACTION_STOP" -> {
                serviceController.stopService()
            }
        }
    }

    private fun loadFolders(context: Context): List<Folder> {
        val sharedPreferences = context.getSharedPreferences("Folders", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("folders", null) ?: return emptyList()
        val type = object : TypeToken<List<Folder>>() {}.type
        return Gson().fromJson(json, type)
    }
}
