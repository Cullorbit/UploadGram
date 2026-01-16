package com.example.photouploaderapp.telegrambot

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
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
        private val networkSemaphore = Semaphore(1)

        private const val TELEGRAM_LIMIT = 49 * 1024 * 1024
        private const val RENDER_PROXY_URL = "https://telegram-bot-api-latest-wuhf.onrender.com"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    suspend fun sendDocument(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        fileName: String
    ): Pair<Boolean, String?> {

        networkSemaphore.acquire()
        try {
            delay(800)

            val isLargeFile = file.length() > TELEGRAM_LIMIT
            val baseUrl = if (isLargeFile) RENDER_PROXY_URL else "https://api.telegram.org"


            var result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, false)

            if (!result.first && (result.second?.contains("400") == true || result.second?.contains("dimensions") == true)) {
                Log.w("TelegramApi", "Retrying $fileName as Document via $baseUrl")
                delay(1000)
                result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, true)
            }

            return result
        } finally {
            networkSemaphore.release()
        }
    }

    private suspend fun executeUpload(
        baseUrl: String,
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

        val finalUrl = "$baseUrl/bot$botToken/$method"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .apply { topicId?.let { addFormDataPart("message_thread_id", it.toString()) } }
            .addFormDataPart(mediaKey, fileName, file.asRequestBody(mediaType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(finalUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(Pair(false, "Network error: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                val isOk = response.isSuccessful && responseBody.contains("\"ok\":true")

                if (isOk) {
                    continuation.resume(Pair(true, null))
                } else {
                    continuation.resume(Pair(false, "Status: ${response.code} - $responseBody"))
                }
                response.close()
            }
        })
    }
}