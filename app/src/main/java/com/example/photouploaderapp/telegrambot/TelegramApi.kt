package com.example.photouploaderapp.telegrambot

import android.util.Log
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class TelegramApi {

    companion object {
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
        try {
            Log.d("TelegramApi", "Worker for '$fileName' waiting for network access...")
            networkSemaphore.acquire()
            Log.d("TelegramApi", "Worker for '$fileName' acquired network access. Starting upload.")

            return suspendCancellableCoroutine { continuation ->
                try {
                    val baseUrl = "https://telegram-bot-api-latest-wuhf.onrender.com"
                    val finalUrl = "$baseUrl/bot$botToken/sendDocument"
                    Log.d("TelegramApi", "Uploading to custom server: $finalUrl")

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", chatId)
                        .apply { topicId?.let { addFormDataPart("message_thread_id", it.toString()) } }
                        .addFormDataPart("document", fileName, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()

                    val request = Request.Builder()
                        .url(finalUrl)
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) continuation.resume(Pair(false, e.message))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseBody = response.body?.string()
                            if (response.isSuccessful && responseBody?.contains("\"ok\":true") == true) {
                                if (continuation.isActive) continuation.resume(Pair(true, null))
                            } else {
                                val errorDetails = "Error: ${response.code} - $responseBody"
                                Log.e("TelegramApi", "Upload failed: $errorDetails")
                                if (continuation.isActive) continuation.resume(Pair(false, errorDetails))
                            }
                            response.close()
                        }
                    })
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(Pair(false, e.message))
                }
            }
        } finally {
            Log.d("TelegramApi", "Worker for '$fileName' released network access.")
            networkSemaphore.release()
        }
    }
}
