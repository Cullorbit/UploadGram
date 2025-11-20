package com.example.photouploaderapp.telegrambot

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.photouploaderapp.R

class TelegramBot(
    private val botToken: String,
    private val chatId: String,
    private val context: Context,
    private val topicId: Int? = null
) {

    companion object {
        private const val TAG = "TelegramBot"
        private const val MAX_RETRIES = 3
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(RetryInterceptor())
        .build()
    private val gson = Gson()

    private inner class RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response: Response? = null
            var retryCount = 0

            try {
                do {
                    response?.close()
                    response = chain.proceed(request)

                    if (response.code == 429) {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 30
                        Log.w(TAG, "Rate limited. Waiting $retryAfter seconds")
                        Thread.sleep(retryAfter * 1000L)
                    }
                } while (retryCount++ < MAX_RETRIES && !response.isSuccessful)

                return response
            } finally {

            }
        }
    }

    private fun handleResponse(
        response: Response,
        fileName: String,
        callback: (Boolean) -> Unit
    ) {
        try {
            val responseBody = response.body ?: run {
                Log.e(TAG, context.getString(R.string.empty_response_body, fileName))
                callback(false)
                return
            }

            val responseString = try {
                responseBody.string()
            } catch (e: IOException) {
                Log.e(TAG, context.getString(R.string.error_reading_response, fileName), e)
                callback(false)
                return
            }

            if (response.isSuccessful) {
                try {
                    val telegramResponse = gson.fromJson(responseString, TelegramResponse::class.java)
                    if (telegramResponse.ok) {
                        Log.d(TAG, context.getString(R.string.file_sent_successfully, fileName))
                        callback(true)
                    } else {
                        Log.e(TAG, context.getString(R.string.error_sending_file, fileName, telegramResponse.description.orEmpty()))
                        callback(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, context.getString(R.string.error_parsing_response, fileName), e)
                    callback(false)
                }
            } else {
                Log.e(TAG, context.getString(R.string.http_error, fileName, response.code))
                Log.d(TAG, context.getString(R.string.server_response, responseString.take(500)))
                callback(false)
            }
        } finally {
            response.close()
        }
    }

    fun sendDocument(file: File, callback: (Boolean) -> Unit) {
        performSendDocument(file, callback)
    }

    fun sendDocument(docFile: DocumentFile, topicId: Int? = this.topicId, callback: (Boolean) -> Unit) { // <--- ДОБАВЛЕН ПАРАМЕТР topicId
        val url = "https://api.telegram.org/bot$botToken/sendDocument"
        val mediaType = "application/octet-stream".toMediaTypeOrNull()
        val inputStream = context.contentResolver.openInputStream(docFile.uri)
        val bytes = inputStream?.readBytes() ?: return
        inputStream?.close()

        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", docFile.name ?: "document", bytes.toRequestBody(mediaType))

        topicId?.let {
            formBuilder.addFormDataPart("message_thread_id", it.toString())
        }

        val requestBody: RequestBody = formBuilder.build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, context.getString(R.string.error_sending_document, docFile.name.orEmpty(), e.message.orEmpty()))
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                handleResponse(response, docFile.name.toString(), callback)
            }
        })
    }

    private fun performSendDocument(file: File, callback: (Boolean) -> Unit, topicId: Int? = this.topicId) {
        val url = "https://api.telegram.org/bot$botToken/sendDocument"
        val mediaType = "application/octet-stream".toMediaTypeOrNull()
        val formBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", file.name, file.asRequestBody(mediaType))

        topicId?.let {
            formBuilder.addFormDataPart("message_thread_id", it.toString())
        }

        val requestBody: RequestBody = formBuilder.build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, context.getString(R.string.error_sending_file_with_message, file.name, e.message.orEmpty()))
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                handleResponse(response, file.name, callback)
            }
        })
    }
}
