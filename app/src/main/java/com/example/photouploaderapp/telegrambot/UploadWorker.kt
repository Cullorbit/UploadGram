package com.example.photouploaderapp.telegrambot

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photouploaderapp.R
import java.io.File
import java.io.FileOutputStream

class UploadWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG = "UploadWorker"
    private val telegramApi = TelegramApi()

    override suspend fun doWork(): Result {
        val botToken = inputData.getString("KEY_BOT_TOKEN") ?: return Result.failure()
        val chatId = inputData.getString("KEY_CHAT") ?: return Result.failure()
        val fileUriString = inputData.getString("KEY_FILE_URI") ?: return Result.failure()
        val originalFileName = inputData.getString("KEY_ORIGINAL_FILE_NAME") ?: return Result.failure()
        val topicId = inputData.getInt("KEY_TOPIC", -1).takeIf { it > 0 }
        val folderName = inputData.getString("KEY_FOLDER_NAME") ?: return Result.failure()

        val cachedFile = createCacheFileFromUri(fileUriString.toUri(), originalFileName)
        if (cachedFile == null) {
            sendLog("ОШИБКА КЭШИРОВАНИЯ: $originalFileName", folderName)
            return Result.failure()
        }

        sendLog("Отправляю файл: $originalFileName", folderName)

        val (isSuccess, errorMessage) = telegramApi.sendDocument(
            botToken = botToken,chatId = chatId,
            topicId = topicId,
            file = cachedFile,
            fileName = originalFileName
        )

        cachedFile.delete()

        if (isSuccess) {
            Log.d(TAG, "Work SUCCESS for $originalFileName")
            sendLog(context.getString(R.string.file_sent_successfully, originalFileName), folderName)
            markFileAsSent(context, fileUriString)
            return Result.success()
        } else {
            Log.w (TAG, "Work FAILURE for $originalFileName. Reason: $errorMessage")
            val errorText = errorMessage ?: context.getString(R.string.error_sending_file_queued, originalFileName)
            sendLog(errorText, folderName)
            return Result.retry()
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
    } catch (e: Exception) {Log.e(TAG, "Failed to create cache file from URI", e)
        null
    }
}

private fun sendLog(message: String, folderName: String) {
    val intent = android.content.Intent("com.example.photouploaderapp.UPLOAD_LOG").apply {
        putExtra("log_message", message)
        putExtra("folder_name", folderName)
    }
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
}

private fun markFileAsSent(context: Context, fileUri: String) {
    val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
    sentFilesPrefs.edit { putBoolean(fileUri, true) }
    Log.d(TAG, "File marked as sent: $fileUri")
}
}



