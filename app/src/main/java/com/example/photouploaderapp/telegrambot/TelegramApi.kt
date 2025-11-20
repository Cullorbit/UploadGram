package com.example.photouploaderapp.telegrambot

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class TelegramApi {
    companion object {
        private const val TAG = "TelegramApi"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS) // 10 минут на отправку
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun sendDocument(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        originalFileName: String
    ): Pair<Boolean, String?> {
        return suspendCancellableCoroutine { continuation ->
            val url = "https://api.telegram.org/bot$botToken/sendDocument"
            try {
                val fileRequestBody = object : RequestBody() {
                    override fun contentType(): MediaType? = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength(): Long = file.length()
                    override fun writeTo(sink: BufferedSink) {
                        file.source().use { source -> sink.writeAll(source) }
                    }
                }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("document", originalFileName, fileRequestBody)
                    .apply { topicId?.let { addFormDataPart("message_thread_id", it.toString()) } }
                    .build()

                val request = Request.Builder().url(url).post(requestBody).build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val errorMessage = "OkHttp Failure: ${e.message ?: "Unknown network error"}"
                        Log.e(TAG, "Failure for $originalFileName", e)
                        if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        handleResponse(response, originalFileName, continuation)
                    }
                })

                continuation.invokeOnCancellation { client.newCall(request).cancel() }

            } catch (e: Exception) {
                val errorMessage = "Request preparation failed: ${e.message}"
                Log.e(TAG, "Failed to prepare request for $originalFileName", e)
                if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
            }
        }
    }

    private fun handleResponse(response: Response, fileName: String, continuation: CancellableContinuation<Pair<Boolean, String?>>) {
        try {
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                val errorDesc = if (response.code == 413) "File is too large (limit is 50 MB)." else response.message
                val errorMessage = "HTTP Error ${response.code}: $errorDesc. Body: ${responseBody?.take(200)}"
                Log.e(TAG, errorMessage)
                if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
                return
            }
            if (responseBody.isNullOrEmpty()) {
                val errorMessage = "Empty response body for $fileName"
                Log.e(TAG, errorMessage)
                if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
                return
            }
            val isSuccess = responseBody.contains("\"ok\":true")
            if (isSuccess) {
                if (continuation.isActive) continuation.resume(Pair(true, null))
            } else {
                val errorMessage = "Telegram API returned 'ok:false'. Response: ${responseBody.take(200)}"
                Log.e(TAG, errorMessage)
                if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Response handling failed: ${e.message}"
            Log.e(TAG, "Error handling response for $fileName", e)
            if (continuation.isActive) continuation.resume(Pair(false, errorMessage))
        } finally {
            response.close()
        }
    }
}