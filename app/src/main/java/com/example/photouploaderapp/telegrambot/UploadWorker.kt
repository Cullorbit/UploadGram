package com.example.photouploaderapp.telegrambot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photouploaderapp.R
import com.example.photouploaderapp.configs.FolderAdapter
import com.example.photouploaderapp.configs.LogHelper
import com.example.photouploaderapp.configs.SettingsManager
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream

class UploadWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG = "UploadWorker"
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        val botToken = inputData.getString("KEY_BOT_TOKEN") ?: return Result.failure()
        val chatId = inputData.getString("KEY_CHAT") ?: return Result.failure()
        val fileUriString = inputData.getString("KEY_FILE_URI") ?: return Result.failure()
        val originalFileName = inputData.getString("KEY_ORIGINAL_FILE_NAME") ?: return Result.failure()
        val topicId = inputData.getInt("KEY_TOPIC", -1).takeIf { it > 0 }
        val folderName = inputData.getString("KEY_FOLDER_NAME") ?: return Result.failure()
        val fileIdentifier = inputData.getString("KEY_FILE_IDENTIFIER") ?: fileUriString

        val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        if (sentFilesPrefs.contains(fileIdentifier)) {
            return Result.success()
        }

        val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
        if (excludedFilesPrefs.contains(fileIdentifier)) {
            Log.d(TAG, "File $originalFileName is excluded from sync")
            return Result.success()
        }

        try {
            cleanCacheIfNeeded()

            val cachedFile = createCacheFileFromUri(fileUriString.toUri(), originalFileName)
            if (cachedFile == null) {
                LogHelper.writeLog(context, context.getString(R.string.error_caching, originalFileName), folderName)
                return Result.failure()
            }

            LogHelper.writeLog(context, context.getString(R.string.sending_file, originalFileName), folderName)

            val (isSuccess, errorMessage) = TelegramApi.sendDocument(
                botToken = botToken,
                chatId = chatId,
                topicId = topicId,
                file = cachedFile,
                fileName = originalFileName,
                proxyUrl = settingsManager.proxyUrl
            )

            cachedFile.delete()

            if (isSuccess) {
                LogHelper.writeLog(context, context.getString(R.string.file_sent_successfully, originalFileName), folderName)
                markFileAsSent(context, fileIdentifier)
                
                val intent = Intent(FolderAdapter.ACTION_PREVIEWS_UPDATED)
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                
                return Result.success()
            } else {
                val finalErrorMessage = if (errorMessage?.contains("timeout", ignoreCase = true) == true) {
                    context.getString(R.string.error_timeout)
                } else if (errorMessage == "ERROR_THREAD_NOT_FOUND") {
                    notifyTopicError(folderName)
                    context.getString(R.string.error_topic_not_found_message, folderName)
                } else {
                    errorMessage ?: context.getString(R.string.error_sending_file_queued, originalFileName)
                }
                
                LogHelper.writeLog(context, finalErrorMessage, folderName)
                return if (errorMessage == "ERROR_THREAD_NOT_FOUND") Result.failure() else Result.retry()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogHelper.writeLog(context, context.getString(R.string.error_generic_with_desc, e.message), folderName)
            return Result.retry()
        }
    }

    private fun cleanCacheIfNeeded() {
        val limit = settingsManager.cacheLimit
        if (limit <= 0) return

        val cacheDir = context.cacheDir
        val files = cacheDir.listFiles()?.toMutableList() ?: return
        
        var currentSize = files.sumOf { it.length() }
        
        if (currentSize > limit) {
            LogHelper.writeLog(context, context.getString(R.string.cache_limit_reached))
            files.sortBy { it.lastModified() }
            
            while (currentSize > limit && files.isNotEmpty()) {
                val fileToDelete = files.removeAt(0)
                val fileSize = fileToDelete.length()
                if (fileToDelete.delete()) {
                    currentSize -= fileSize
                }
            }
        }
    }

    private fun createCacheFileFromUri(uri: Uri, fileName: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "${System.currentTimeMillis()}-$fileName")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun markFileAsSent(context: Context, identifier: String) {
        val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        sentFilesPrefs.edit { putBoolean(identifier, true) }
    }

    private fun notifyTopicError(folderName: String) {
        val intent = Intent("com.example.photouploaderapp.TOPIC_ERROR")
        intent.putExtra("folder_name", folderName)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
