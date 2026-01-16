package com.example.photouploaderapp.telegrambot

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TelegramApi {

    companion object {
        // Решение проблемы 2: Ограничиваем до 1 одновременного запроса на всё приложение
        private val networkSemaphore = Semaphore(1)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }

    suspend fun sendDocument(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        fileName: String
    ): Pair<Boolean, String?> {
        // Ждем своей очереди (если файлов 4000, они будут проходить по одному)
        networkSemaphore.acquire()
        try {
            // Небольшая пауза, чтобы Telegram не посчитал нас спамером
            delay(800)

            // Попытка 1: Стандартная отправка
            val firstTry = executeUpload(botToken, chatId, topicId, file, fileName, false)

            // Решение проблемы 1: Если ошибка 400 или "PHOTO_INVALID_DIMENSIONS"
            if (!firstTry.first && (firstTry.second?.contains("400") == true || firstTry.second?.contains("dimensions") == true)) {
                Log.w("TelegramApi", "Invalid dimensions for $fileName, retrying as Document")
                return executeUpload(botToken, chatId, topicId, file, fileName, true)
            }

            return firstTry
        } finally {
            networkSemaphore.release()
        }
    }

    private suspend fun executeUpload(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        fileName: String,
        forceDocument: Boolean
    ): Pair<Boolean, String?> = suspendCoroutine { continuation ->

        val fileExtension = fileName.substringAfterLast('.', "").lowercase()

        val (method, mediaKey, mediaType) = when {
            forceDocument -> Triple("sendDocument", "document", "application/octet-stream")
            fileExtension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> Triple("sendPhoto", "photo", "image/$fileExtension")
            fileExtension in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv") -> Triple("sendVideo", "video", "video/$fileExtension")
            fileExtension in listOf("mp3", "m4a", "ogg", "wav", "flac") -> Triple("sendAudio", "audio", "audio/$fileExtension")
            else -> Triple("sendDocument", "document", "application/octet-stream")
        }

        val url = "https://api.telegram.org/bot$botToken/$method"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .apply { topicId?.let { addFormDataPart("message_thread_id", it.toString()) } }
            .addFormDataPart(mediaKey, fileName, file.asRequestBody(mediaType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(Pair(false, e.message))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful && responseBody.contains("\"ok\":true")) {
                    continuation.resume(Pair(true, null))
                } else {
                    continuation.resume(Pair(false, "Error: ${response.code} - $responseBody"))
                }
                response.close()
            }
        })
    }
}
