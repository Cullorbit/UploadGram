package com.example.photouploaderapp.telegrambot

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photouploaderapp.R
import java.io.File

class UploadWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG = "UploadWorker"
    private val telegramApi = TelegramApi()

    override suspend fun doWork(): Result {
        val botToken = inputData.getString("KEY_BOT_TOKEN")
        val chatId = inputData.getString("KEY_CHAT")
        val filePath = inputData.getString("KEY_FILE_PATH")
        val originalFileName = inputData.getString("KEY_ORIGINAL_FILE_NAME")
        val topicId = inputData.getInt("KEY_TOPIC", -1).takeIf { it > 0 }
        val folderName = inputData.getString("KEY_FOLDER_NAME")
        val mediaType = inputData.getString("KEY_MEDIA_TYPE")
        val isLastPart = inputData.getBoolean("KEY_IS_LAST_PART", true)
        val originalFileForMark = inputData.getString("KEY_ORIGINAL_FILE_FOR_MARK") ?: ""


        if (botToken.isNullOrEmpty() || chatId.isNullOrEmpty() || filePath.isNullOrEmpty() || originalFileName.isNullOrEmpty() || folderName.isNullOrEmpty() || mediaType.isNullOrEmpty()) {
            Log.e(TAG, "Worker failed: Missing input data.")
            return Result.failure()
        }

        val cachedFile = File(filePath)
        if (!cachedFile.exists()) {
            Log.e(TAG, "Worker failed: Cached file does not exist at $filePath")
            sendLog("ОШИБКА: Кэшированный файл не найден: $originalFileName", folderName)
            return Result.failure()
        }

        val (isSuccess, errorMessage) = telegramApi.sendDocument(
            botToken = botToken,
            chatId = chatId,
            topicId = topicId,
            file = cachedFile,
            originalFileName = originalFileName
        )

        if (isSuccess) {
            Log.d(TAG, "Work SUCCESS for $originalFileName")
            sendLog(context.getString(R.string.file_sent_successfully, originalFileName), folderName)
            cachedFile.delete()

            // Отмечаем ВЕСЬ файл как отправленный только после отправки ПОСЛЕДНЕЙ части
            if (isLastPart) {
                markFileAsSent(context, originalFileForMark, folderName, mediaType)
            }
            return Result.success()
        } else {
            Log.w(TAG, "Work FAILURE for $originalFileName. Reason: $errorMessage")
            val errorText = errorMessage ?: context.getString(R.string.error_sending_file_queued, originalFileName)
            sendLog(errorText, folderName)
            // Если ошибка, проваливаем всю цепочку
            return Result.failure()
        }
    }

    private fun sendLog(message: String, folderName: String) {
        val intent = android.content.Intent("com.example.photouploaderapp.UPLOAD_LOG").apply {
            putExtra("log_message", message)
            putExtra("folder_name", folderName)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun markFileAsSent(context: Context, fileName: String, folderName: String, mediaType: String) {
        val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val uniqueFileId = "${folderName}_${mediaType}_$fileName"
        sentFilesPrefs.edit { putBoolean(uniqueFileId, true) }
        Log.d(TAG, "Original file marked as sent: $uniqueFileId")
    }
}
    