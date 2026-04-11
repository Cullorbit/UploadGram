package com.example.photouploaderapp.telegrambot

import android.util.Log
import com.example.photouploaderapp.R
import com.example.photouploaderapp.configs.MyApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
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

            if (!result.first && result.second?.contains("Status: 429") == true) {
                val retryAfter = extractRetryAfter(result.second ?: "")
                val waitMs = (retryAfter ?: 30) * 1000L + 2000L
                delay(waitMs)
                result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, false)
            }

            if (!result.first && (result.second?.contains("400") == true || result.second?.contains("dimensions") == true)) {
                delay(1000)
                result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, true)

                if (!result.first && result.second?.contains("Status: 429") == true) {
                    val retryAfter = extractRetryAfter(result.second ?: "")
                    val waitMs = (retryAfter ?: 30) * 1000L + 2000L
                    delay(waitMs)
                    result = executeUpload(baseUrl, botToken, chatId, topicId, file, fileName, true)
                }
            }

            delay(3500)

            return result
        } finally {
            networkSemaphore.release()
        }
    }

    private fun extractRetryAfter(errorMessage: String): Int? {
        return try {
            val jsonPart = errorMessage.substringAfter(" - ").trim()
            val json = JSONObject(jsonPart)
            json.optJSONObject("parameters")?.optInt("retry_after")
        } catch (e: Exception) {
            null
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

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                val context = MyApplication.getContext()
                val errorMsg = if (e is java.net.SocketTimeoutException) {
                    "${context.getString(R.string.error_timeout)}: ${context.getString(R.string.error_timeout_details)}"
                } else {
                    context.getString(R.string.error_network_with_desc, e.message ?: "")
                }
                continuation.resume(Pair(false, errorMsg))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                    return
                }
                val context = MyApplication.getContext()
                val responseBody = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
                val isOk = response.isSuccessful && responseBody.contains("\"ok\":true")

                if (isOk) {
                    continuation.resume(Pair(true, null))
                } else {
                    val msg = when {
                        response.code == 413 -> context.getString(R.string.file_too_large)
                        response.code == 400 && responseBody.contains("message thread not found") -> "ERROR_THREAD_NOT_FOUND"
                        else -> "Status: ${response.code} - $responseBody"
                    }
                    continuation.resume(Pair(false, msg))
                }
                response.close()
            }
        })
    }
}
