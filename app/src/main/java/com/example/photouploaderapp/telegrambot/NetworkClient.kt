package com.example.photouploaderapp.telegrambot

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES) 
            .readTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
