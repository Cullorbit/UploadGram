package com.example.photouploaderapp.telegrambot

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

class TelegramApi {
    private val client = OkHttpClient()

    suspend fun sendDocument(
        botToken: String,
        chatId: String,
        topicId: Int?,
        file: File,
        fileName: String
    ): Pair<Boolean, String?> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .apply { topicId?.let { addFormDataPart("message_thread_id", it.toString()) } }
                    .addFormDataPart("document", fileName, file.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendDocument")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(Pair(false, e.message))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody?.contains("\"ok\":true") == true) {
                            continuation.resume(Pair(true, null))
                        } else {
                            continuation.resume(Pair(false, "Error: ${response.code} - $responseBody"))
                        }
                        response.close()
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(Pair(false, e.message))
            }
        }
    }
}
