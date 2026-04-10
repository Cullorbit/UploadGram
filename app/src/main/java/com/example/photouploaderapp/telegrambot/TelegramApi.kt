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
import kotlin.coroutines.resume

object TelegramApi {

    private val networkSemaphore = Semaphore(1)
    private const val TELEGRAM_LIMIT = 49 * 1024 * 1024
    private val client = NetworkClient.client

    suspend fun sendDocument(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        fileName: String,
        proxyUrl: String
    ): Pair<Boolean, String?> {

        networkSemaphore.acquire()
        try {
            val isLargeFile = file.length() > TELEGRAM_LIMIT
            val baseUrl = if (isLargeFile) proxyUrl else "https://api.telegram.org"

            var result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, false)

            if (!result.first && (result.second?.contains("400") == true || result.second?.contains("dimensions") == true)) {
                Log.w("TelegramApi", "Retrying $fileName as Document via $baseUrl")
                delay(1000)
                result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, true)
            }

            delay(1500)

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
    ): Pair<Boolean, String?> = suspendCancellableCoroutine { continuation ->

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

        val call = client.newCall(request)

        // Обеспечиваем отмену запроса OkHttp при отмене корутины
        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                val errorMsg = if (e is java.net.SocketTimeoutException) {
                    "Timeout error: загрузка длилась слишком долго"
                } else {
                    "Network error: ${e.message}"
                }
                continuation.resume(Pair(false, errorMsg))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                    return
                }
                val responseBody = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
                val isOk = response.isSuccessful && responseBody.contains("\"ok\":true")

                if (isOk) {
                    continuation.resume(Pair(true, null))
                } else {
                    val msg = if (response.code == 413) "Файл слишком большой для Telegram" else "Status: ${response.code} - $responseBody"
                    continuation.resume(Pair(false, msg))
                }
                response.close()
            }
        })
    }
}
