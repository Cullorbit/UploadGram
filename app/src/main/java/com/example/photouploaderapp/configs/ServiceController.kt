package com.example.photouploaderapp.configs

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.example.photouploaderapp.R
import com.example.photouploaderapp.telegrambot.ScanWorker
import com.example.photouploaderapp.telegrambot.UploadWorker
import com.example.photouploaderapp.telegrambot.SyncForegroundService
import java.util.concurrent.TimeUnit

class ServiceController(private val context: Context, private val settingsManager: SettingsManager) {

    private val workManager = WorkManager.getInstance(context)
    private val TAG = "ServiceController"

    fun startService(folders: List<Folder>) {
        val networkUtils = NetworkUtils(context)
        val requiredNetwork = if (settingsManager.syncOption == "wifi_only") {
            if (!networkUtils.isWifiConnected()) {
                LogHelper.writeLog(context, context.getString(R.string.sync_available_wifi_only))
                return
            }
            NetworkType.UNMETERED
        } else {
            if (!networkUtils.isConnected()) {
                LogHelper.writeLog(context, context.getString(R.string.no_internet_connection))
                return
            }
            NetworkType.CONNECTED
        }

        val immediateScanRequest = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(requiredNetwork).build())
            .setInputData(workDataOf("is_periodic" to false))
            .addTag("immediate_scan_work")
            .build()

        workManager.enqueueUniqueWork(
            "ImmediateScan",
            ExistingWorkPolicy.REPLACE,
            immediateScanRequest
        )

        val periodicScanRequest = PeriodicWorkRequestBuilder<ScanWorker>(
            settingsManager.syncInterval, TimeUnit.MILLISECONDS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(requiredNetwork).build())
            .setInputData(workDataOf("is_periodic" to true))
            .addTag("periodic_scan_work")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "FolderScanWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicScanRequest
        )

        updateForegroundService(true)
        LogHelper.writeLog(context, context.getString(R.string.initial_scan_started))
        logNextSyncTime()
    }

    private fun updateForegroundService(isRunning: Boolean) {
        settingsManager.isServiceRunning = isRunning
        
        val serviceIntent = Intent(context, SyncForegroundService::class.java).apply {
            putExtra("is_running", isRunning)
        }
        
        if (isRunning) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            context.startService(serviceIntent)
        }

        val stateIntent = Intent("com.example.photouploaderapp.SERVICE_STATE_CHANGED").apply {
            putExtra("is_running", isRunning)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(stateIntent)
    }

    fun scanFolders(folders: List<Folder>, isPeriodic: Boolean) {
        val message = if (isPeriodic) {
            context.getString(R.string.scan_periodic_check)
        } else {
            context.getString(R.string.scan_initial_check)
        }
        if (isPeriodic) {
            LogHelper.writeLog(context, message)
        }

        val networkType = if (settingsManager.syncOption == "wifi_only") NetworkType.UNMETERED else NetworkType.CONNECTED
        val allUploadRequests = mutableListOf<OneTimeWorkRequest>()

        folders.filter { it.isSyncing }.forEach { folder ->
            val requestsForFolder = createWorkRequestsForFolder(folder, networkType)
            allUploadRequests.addAll(requestsForFolder)
        }

        if (allUploadRequests.isEmpty()) {
            if (!isPeriodic) LogHelper.writeLog(context, context.getString(R.string.no_new_files_found))
        } else {
            var continuation = workManager.beginUniqueWork(
                "FileUploadChain",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                allUploadRequests.first()
            )

            allUploadRequests.drop(1).forEach { workRequest ->
                continuation = continuation.then(workRequest)
            }

            continuation.enqueue()
            LogHelper.writeLog(context, context.getString(R.string.scan_found_files, allUploadRequests.size))
        }
    }

    private fun logNextSyncTime() {
        var intervalMillis = settingsManager.syncInterval
        if (intervalMillis < TimeUnit.MINUTES.toMillis(15)) {
            intervalMillis = TimeUnit.MINUTES.toMillis(15)
        }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(intervalMillis)
        LogHelper.writeLog(context, context.getString(R.string.service_configured_next_check, minutes))
    }

    private fun createWorkRequestsForFolder(folder: Folder, networkType: NetworkType): List<OneTimeWorkRequest> {
        val requests = mutableListOf<OneTimeWorkRequest>()
        try {
            val folderUri = folder.path.toUri()
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)

            if (documentFolder == null || !documentFolder.canRead()) {
                LogHelper.writeLog(context, context.getString(R.string.error_folder_access, folder.name), context.getString(R.string.system_log_name))
                return emptyList()
            }

            documentFolder.listFiles().forEach { docFile ->
                if (!docFile.isFile) return@forEach
                val fileName = docFile.name ?: return@forEach
                if (fileName.startsWith(".")) return@forEach

                // Создаем уникальный идентификатор: ID папки + имя файла + размер
                val fileIdentifier = "folder_${folder.id}_${fileName}_${docFile.length()}"

                if (!sentFilesPrefs.contains(fileIdentifier) && isValidMedia(docFile, folder.mediaType)) {
                    val inputData = workDataOf(
                        "KEY_BOT_TOKEN" to (folder.botToken.ifEmpty { settingsManager.botToken ?: "" }),
                        "KEY_CHAT" to (folder.chatId.ifEmpty { settingsManager.chatId ?: "" }),
                        "KEY_FILE_URI" to docFile.uri.toString(),
                        "KEY_ORIGINAL_FILE_NAME" to fileName,
                        "KEY_TOPIC" to (folder.getTopicId() ?: -1),
                        "KEY_FOLDER_NAME" to folder.name,
                        "KEY_MEDIA_TYPE" to folder.mediaType,
                        "KEY_FILE_IDENTIFIER" to fileIdentifier
                    )
                    val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                        .addTag("upload_work")
                        .addTag("folder_id_${folder.id}")
                        .build()
                    requests.add(uploadWorkRequest)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder ${folder.name}", e)
            LogHelper.writeLog(context, context.getString(R.string.error_critical_scan, folder.name, e.message), context.getString(R.string.system_log_name))
        }
        return requests
    }

    private fun isValidMedia(file: DocumentFile, mediaType: String): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        val isFilePhoto = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { fileName.endsWith(it) }
        val isFileVideo = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { fileName.endsWith(it) }
        val isFileAudio = listOf(".mp3", ".m4a", ".ogg", ".wav", ".flac").any { fileName.endsWith(it) }

        val photoTypeString = context.getString(R.string.only_photo)
        val videoTypeString = context.getString(R.string.only_video)
        val audioTypeString = context.getString(R.string.only_audio)
        val allTypeString = context.getString(R.string.all_media)

        return when (mediaType) {
            photoTypeString -> isFilePhoto
            videoTypeString -> isFileVideo
            audioTypeString -> isFileAudio
            allTypeString -> isFilePhoto || isFileVideo || isFileAudio
            else -> false
        }
    }

    fun stopService() {
        workManager.cancelAllWorkByTag("upload_work")
        workManager.cancelUniqueWork("FolderScanWork")
        workManager.cancelUniqueWork("ImmediateScan")
        workManager.cancelUniqueWork("FileUploadChain")
        updateForegroundService(false)
        LogHelper.writeLog(context, context.getString(R.string.service_stopped))
    }

    fun cancelPeriodicScan() {
        workManager.cancelUniqueWork("FolderScanWork")
    }
}
