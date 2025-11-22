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
import java.io.File
import java.io.FileOutputStream
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
            .setInputData(workDataOf("is_periodic" to false)) // Помечаем как НЕ периодическую
            .addTag("immediate_scan_work")
            .build()

        workManager.enqueueUniqueWork(
            "ImmediateScan",
            ExistingWorkPolicy.REPLACE,
            immediateScanRequest
        )

        sendLogToUI("Запущена первичная проверка папок...")
    }

    fun scanFolders(folders: List<Folder>, isPeriodic: Boolean) {
        val message = if (isPeriodic) "Периодическая проверка папок..." else "Первичная проверка папок..."
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
            sendLogToUI("Новых файлов не найдено.")
        } else {
            workManager.beginUniqueWork("FileUploadChain", ExistingWorkPolicy.REPLACE, allUploadRequests.first())
                .then(allUploadRequests.drop(1))
                .enqueue()
            sendLogToUI("Найдено ${allUploadRequests.size} новых файлов. Задачи поставлены в очередь.")
        }

        if (!isPeriodic) {
            setupPeriodicScan(networkType)
        }
    }

    private fun setupPeriodicScan(requiredNetwork: NetworkType) {
        var intervalMillis = settingsManager.syncInterval
        if (intervalMillis < TimeUnit.MINUTES.toMillis(15)) {
            intervalMillis = TimeUnit.MINUTES.toMillis(15)
        }
        val intervalToShow = TimeUnit.MILLISECONDS.toMinutes(intervalMillis)
        val intervalUnitToShow = getIntervalUnit(intervalToShow)
        val displayValue = if (intervalToShow >= 60) intervalToShow / 60 else intervalToShow

        val constraints = Constraints.Builder().setRequiredNetworkType(requiredNetwork).build()
        val periodicScanRequest = PeriodicWorkRequestBuilder<ScanWorker>(intervalMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf("is_periodic" to true))
            .addTag("periodic_scan_work")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "FolderScanWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicScanRequest
        )
        sendLogToUI("Сервис настроен. Следующая проверка будет через $displayValue $intervalUnitToShow.")
    }

    private fun getIntervalUnit(minutes: Long): String {
        if (minutes < 60) return "минут"
        val hours = minutes / 60
        return when {
            hours % 10 == 1L && hours % 100 != 11L -> "час"
            hours % 10 in 2..4 && (hours % 100 < 10 || hours % 100 >= 20) -> "часа"
            else -> "часов"
        }
    }

    private fun createWorkRequestsForFolder(folder: Folder, networkType: NetworkType): List<OneTimeWorkRequest> {
        val requests = mutableListOf<OneTimeWorkRequest>()
        try {
            val folderUri = folder.path.toUri()
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)

            if (documentFolder == null || !documentFolder.canRead()) {
                sendLogToUI("Ошибка: нет доступа к папке '${folder.name}'. Попробуйте выбрать ее заново.", "СИСТЕМА")
                return emptyList()
            }

            documentFolder.listFiles().forEach { docFile ->
                val fileName = docFile.name ?: return@forEach
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
            sendLogToUI("Критическая ошибка при сканировании папки '${folder.name}': ${e.message}", "СИСТЕМА")
        }
        return requests
    }

    private fun sendLogToUI(message: String, folderName: String = "СИСТЕМА") {
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
        val photoTypeString = context.getString(R.string.only_photo).lowercase()
        val videoTypeString = context.getString(R.string.only_video).lowercase()
        val allTypeString = context.getString(R.string.all_media).lowercase()
        return when (mediaType.lowercase()) {
            photoTypeString -> isFilePhoto
            videoTypeString -> isFileVideo
            allTypeString -> isFilePhoto || isFileVideo
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
}
