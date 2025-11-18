package com.example.photouploaderapp.configs

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.photouploaderapp.telegrambot.UploadService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FolderSyncService : Service() {

    companion object {
        private const val TAG = "FolderSyncService"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val gson = Gson()
    private lateinit var settingsManager: SettingsManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var logHelper: LogHelper

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        notificationHelper = NotificationHelper(this)
        logHelper = LogHelper(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        scope.launch {
            while (isActive) {
                syncFolders()
                delay(settingsManager.syncInterval)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        job.cancel()
    }

    private suspend fun syncFolders() {
        val sharedPreferences = getSharedPreferences("Folders", Context.MODE_PRIVATE)
        val foldersJson = sharedPreferences.getString("folders", null)
        if (foldersJson != null) {
            val type = object : TypeToken<MutableList<Folder>>() {}.type
            val folders: MutableList<Folder> = gson.fromJson(foldersJson, type)

            folders.forEach { folder ->
                if (folder.isSyncing) {
                    syncFolder(folder)
                }
            }
        }
    }

    private suspend fun syncFolder(folder: Folder) {
        val directory = File(folder.path)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles() ?: return
            val totalFiles = files.size

            files.forEachIndexed { index, file ->
                if (file.isFile) {
                    val fileExtension = file.extension.lowercase(Locale.ROOT).trim()

                    // Добавляем логику фильтрации для проверки расширений
                    if (shouldSyncFile(fileExtension, folder.mediaType)) {
                        Log.d(TAG, "File ${file.name} passed filter for ${folder.name}, sending to topic ${folder.topic}")
                        uploadFileToTelegram(file, folder.topic, folder, index, totalFiles)
                    } else {
                        Log.d(TAG, "File ${file.name} skipped for folder ${folder.name}")
                    }
                }
            }
        }
    }

    private fun shouldSyncFile(fileExtension: String, mediaType: String): Boolean {
        val normalizedMediaType = mediaType.lowercase(Locale.ROOT).trim()

        Log.d(TAG, "Checking file extension: ${fileExtension}, expected media type: ${normalizedMediaType}")

        return when (normalizedMediaType) {
            "фото", "photo" -> {
                val isPhoto = fileExtension == "jpg" || fileExtension == "jpeg" || fileExtension == "png"
                Log.d(TAG, "File $fileExtension is photo: $isPhoto")
                isPhoto
            }
            "видео", "video" -> {
                val isVideo = fileExtension == "mp4" || fileExtension == "avi" || fileExtension == "mov"
                Log.d(TAG, "File $fileExtension is video: $isVideo")
                isVideo
            }
            "все", "all" -> {
                val isPhotoOrVideo = fileExtension == "jpg" || fileExtension == "jpeg" ||
                        fileExtension == "png" || fileExtension == "mp4" ||
                        fileExtension == "avi" || fileExtension == "mov"
                Log.d(TAG, "File $fileExtension is photo/video for 'all' media type: $isPhotoOrVideo")
                isPhotoOrVideo
            }
            else -> {
                Log.d(TAG, "Unknown media type: $mediaType")
                false
            }
        }
    }

    private suspend fun uploadFileToTelegram(file: File, topic: String, folder: Folder, fileIndex: Int, totalFiles: Int) {
        val intent = Intent(applicationContext, UploadService::class.java)
        intent.putExtra("KEY_BOT_TOKEN", folder.botToken)
        intent.putExtra("KEY_CHAT", folder.chatId)

        Log.d(TAG, "Sending file ${file.name} to topic: $topic")

        val topicId = topic.toIntOrNull() ?: -1
        intent.putExtra("KEY_TOPIC", topicId)
        intent.putExtra("KEY_TOPIC_ENABLED", folder.isTopicEnabled)
        intent.putExtra("KEY_MEDIA_TYPE", folder.mediaType)
        intent.putExtra("KEY_SYNC_FOLDER", folder.path)
        intent.putExtra("KEY_FOLDER_NAME", folder.name)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "Uploading file: ${file.name} to topic: $topicId at $timestamp, Folder: ${folder.name}, Syncing: ${folder.isSyncing}")

        if (!folder.isSyncing) {
            notificationHelper.showNotification("Синхронизация отменена", "Синхронизация папки ${folder.name} отменена.")
            logHelper.log("Синхронизация папки ${folder.name} отменена.")
            return
        }

        val progress = ((fileIndex + 1).toFloat() / totalFiles.toFloat() * 100).toInt()
        logHelper.log("Загрузка файла: ${file.name} (прогресс: $progress%)")

        if (progress == 100) {
            notificationHelper.showNotification("Синхронизация завершена", "Синхронизация папки ${folder.name} завершена.")
            logHelper.log("Синхронизация папки ${folder.name} завершена.")
        }
        applicationContext.startService(intent)
    }
}
