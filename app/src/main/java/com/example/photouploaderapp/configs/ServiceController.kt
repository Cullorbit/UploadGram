package com.example.photouploaderapp.configs

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.example.photouploaderapp.telegrambot.UploadWorker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ServiceController(private val context: Context, private val settingsManager: SettingsManager) {

    private val workManager = WorkManager.getInstance(context)
    private val TAG = "ServiceController"
    private val CHUNK_SIZE_BYTES = 49 * 1024 * 1024L

    fun startService(folders: List<Folder>) {
        val networkUtils = NetworkUtils(context)
        val requiredNetwork = if (settingsManager.syncOption == "wifi_only") {
            if (!networkUtils.isWifiConnected()) {
                (context as? MainActivity)?.showToast(context.getString(R.string.sync_available_wifi_only))
                return
            }
            NetworkType.UNMETERED
        } else {
            if (!networkUtils.isConnected()) {
                (context as? MainActivity)?.showToast(context.getString(R.string.no_internet_connection))
                return
            }
            NetworkType.CONNECTED
        }
        folders.filter { it.isSyncing && it.path.isNotEmpty() }.forEach { folder ->
            scanAndEnqueue(folder, requiredNetwork)
        }
    }

    private fun scanAndEnqueue(folder: Folder, networkType: NetworkType) {
        val folderUri = folder.path.toUri()
        val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
        val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)

        documentFolder?.listFiles()?.forEach { docFile ->
            val fileName = docFile.name ?: return@forEach
            val uniqueFileId = "${folder.name}_${folder.mediaType}_$fileName"
            if (docFile.isFile && !sentFilesPrefs.contains(uniqueFileId) && isValidMedia(docFile, folder.mediaType)) {
                val cachedFiles = cacheAndSplitFile(docFile)
                if (cachedFiles.isEmpty()) {
                    Log.e(TAG, "Failed to cache or split file: $fileName")
                    return@forEach
                }

                val workRequests = mutableListOf<OneTimeWorkRequest>()
                cachedFiles.forEachIndexed { index, cachedFile ->
                    val isLastPart = (index == cachedFiles.size - 1)
                    val partFileName = if (cachedFiles.size > 1) "${fileName}.part${index + 1}" else fileName

                    val inputData = workDataOf(
                        "KEY_BOT_TOKEN" to (folder.botToken.ifEmpty { settingsManager.botToken ?: "" }),
                        "KEY_CHAT" to (folder.chatId.ifEmpty { settingsManager.chatId ?: "" }),
                        "KEY_FILE_PATH" to cachedFile.absolutePath,
                        "KEY_ORIGINAL_FILE_NAME" to partFileName,
                        "KEY_TOPIC" to (folder.getTopicId() ?: -1),
                        "KEY_FOLDER_NAME" to folder.name,
                        "KEY_MEDIA_TYPE" to folder.mediaType,
                        "KEY_IS_LAST_PART" to isLastPart,
                        "KEY_ORIGINAL_FILE_FOR_MARK" to fileName // Оригинальное имя для финальной отметки
                    )
                    val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                        .addTag(uniqueFileId)
                        .addTag("upload_work")
                        .build()
                    workRequests.add(uploadWorkRequest)
                }

                workManager.beginUniqueWork(uniqueFileId, ExistingWorkPolicy.KEEP, workRequests)
                    .enqueue()

                Log.d(TAG, "Enqueued ${workRequests.size} part(s) for: $fileName in folder ${folder.name}")
            }
        }
    }

    private fun cacheAndSplitFile(docFile: DocumentFile): List<File> {
        val originalFileSize = docFile.length()
        val tempFiles = mutableListOf<File>()

        try {
            context.contentResolver.openInputStream(docFile.uri)?.use { inputStream ->
                if (originalFileSize <= CHUNK_SIZE_BYTES) {
                    val tempFile = File(context.cacheDir, "${System.currentTimeMillis()}-${docFile.name}")
                    FileOutputStream(tempFile).use { outputStream -> inputStream.copyTo(outputStream) }
                    tempFiles.add(tempFile)
                } else {
                    var partNumber = 1
                    val buffer = ByteArray(16 * 1024)
                    var bytesCopied: Long = 0
                    while (bytesCopied < originalFileSize) {
                        val partFile = File(context.cacheDir, "${System.currentTimeMillis()}-${docFile.name}.part$partNumber")
                        var currentPartSize: Long = 0
                        FileOutputStream(partFile).use { outputStream ->
                            while (currentPartSize < CHUNK_SIZE_BYTES) {
                                val bytesToRead = minOf(buffer.size.toLong(), CHUNK_SIZE_BYTES - currentPartSize).toInt()
                                val bytesRead = inputStream.read(buffer, 0, bytesToRead)
                                if (bytesRead == -1) break
                                outputStream.write(buffer, 0, bytesRead)
                                currentPartSize += bytesRead
                            }
                        }
                        tempFiles.add(partFile)
                        bytesCopied += currentPartSize
                        partNumber++
                    }
                }
            } ?: run {
                sendLogToUI("ОШИБКА КЭШИРОВАНИЯ: Не удалось получить InputStream", "СИСТЕМА")
            }
        } catch (e: Exception) {
            val errorMessage = "ОШИБКА КЭШИРОВАНИЯ: ${e.message}"
            Log.e(TAG, "Failed to cache or split file: ${docFile.name}", e)
            sendLogToUI(errorMessage, "СИСТЕМА")
            tempFiles.forEach { it.delete() }
            return emptyList()
        }
        return tempFiles
    }

    private fun sendLogToUI(message: String, folderName: String) {
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
        workManager.cancelAllWork()
        (context as? MainActivity)?.showToast(context.getString(R.string.service_stopped))
    }
}
    