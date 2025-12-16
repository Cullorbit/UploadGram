package com.example.photouploaderapp.configs

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.example.photouploaderapp.R
import com.example.photouploaderapp.telegrambot.ScanWorker
import com.example.photouploaderapp.telegrambot.UploadWorker
import java.util.concurrent.TimeUnit

class ServiceController(private val context: Context, private val settingsManager: SettingsManager) {

    private val workManager = WorkManager.getInstance(context)
    private val TAG = "ServiceController"

    fun startService(folders: List<Folder>) {
        val networkUtils = NetworkUtils(context)
        val requiredNetwork = if (settingsManager.syncOption == "wifi_only") {
            if (!networkUtils.isWifiConnected()) {
                sendLogToUI(context.getString(R.string.sync_available_wifi_only))
                return
            }
            NetworkType.UNMETERED
        } else {
            if (!networkUtils.isConnected()) {
                sendLogToUI(context.getString(R.string.no_internet_connection))
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

        sendLogToUI(context.getString(R.string.initial_scan_started))
        logNextSyncTime()
    }

    fun scanFolders(folders: List<Folder>, isPeriodic: Boolean) {
        val message = if (isPeriodic) {
            context.getString(R.string.scan_periodic_check)
        } else {
            context.getString(R.string.scan_initial_check)
        }
        if (isPeriodic) {
            sendLogToUI(message)
        }

        val networkType = if (settingsManager.syncOption == "wifi_only") NetworkType.UNMETERED else NetworkType.CONNECTED
        val allUploadRequests = mutableListOf<OneTimeWorkRequest>()

        folders.filter { it.isSyncing }.forEach { folder ->
            val requestsForFolder = createWorkRequestsForFolder(folder, networkType)
            allUploadRequests.addAll(requestsForFolder)
        }

        if (allUploadRequests.isEmpty()) {
            sendLogToUI(context.getString(R.string.no_new_files_found))
        } else {
            workManager.beginUniqueWork("FileUploadChain", ExistingWorkPolicy.REPLACE, allUploadRequests.first())
                .then(allUploadRequests.drop(1))
                .enqueue()
            sendLogToUI(context.getString(R.string.scan_found_files, allUploadRequests.size))
        }
    }

    private fun logNextSyncTime() {
        var intervalMillis = settingsManager.syncInterval
        if (intervalMillis < TimeUnit.MINUTES.toMillis(15)) {
            intervalMillis = TimeUnit.MINUTES.toMillis(15)
        }
        val minutes = TimeUnit.MILLISECONDS.toMinutes(intervalMillis)
        sendLogToUI(context.getString(R.string.service_configured_next_check, minutes))
    }

    private fun createWorkRequestsForFolder(folder: Folder, networkType: NetworkType): List<OneTimeWorkRequest> {
        val requests = mutableListOf<OneTimeWorkRequest>()
        try {
            val folderUri = folder.path.toUri()
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)

            if (documentFolder == null || !documentFolder.canRead()) {
                sendLogToUI(context.getString(R.string.error_folder_access, folder.name), context.getString(R.string.system_log_name))
                return emptyList()
            }

            documentFolder.listFiles().forEach { docFile ->
                val fileName = docFile.name ?: return@forEach

                if (fileName.startsWith(".")) {
                    Log.d(TAG, context.getString(R.string.log_skipping_hidden_file, fileName))
                    return@forEach
                }

                val uniqueFileId = docFile.uri.toString()

                if (docFile.isFile && !sentFilesPrefs.contains(uniqueFileId) && isValidMedia(docFile, folder.mediaType)) {
                    val inputData = workDataOf(
                        "KEY_BOT_TOKEN" to (folder.botToken.ifEmpty { settingsManager.botToken ?: "" }),
                        "KEY_CHAT" to (folder.chatId.ifEmpty { settingsManager.chatId ?: "" }),
                        "KEY_FILE_URI" to docFile.uri.toString(),
                        "KEY_ORIGINAL_FILE_NAME" to fileName,
                        "KEY_TOPIC" to (folder.getTopicId() ?: -1),
                        "KEY_FOLDER_NAME" to folder.name,
                        "KEY_MEDIA_TYPE" to folder.mediaType
                    )
                    val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                        .addTag("upload_work")
                        .build()
                    requests.add(uploadWorkRequest)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder ${folder.name}", e)
            sendLogToUI(context.getString(R.string.error_critical_scan, folder.name, e.message), context.getString(R.string.system_log_name))
        }
        return requests
    }

    private fun sendLogToUI(message: String, folderName: String = context.getString(R.string.system_log_name)) {
        val intent = android.content.Intent("com.example.photouploaderapp.UPLOAD_LOG").apply {
            putExtra("log_message", message)
            putExtra("folder_name", folderName)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
        sendLogToUI(context.getString(R.string.service_stopped))
    }

    fun cancelPeriodicScan() {
        workManager.cancelUniqueWork("FolderScanWork")
    }
}
