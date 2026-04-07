package com.example.photouploaderapp.telegrambot

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    // Увеличиваем таймауты для стабильной работы с большими файлами и медленными прокси
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES) // 30 минут на загрузку (для очень больших видео или плохого интернета)
            .readTimeout(10, TimeUnit.MINUTES)  // 10 минут на ожидание ответа от сервера после загрузки
            .retryOnConnectionFailure(true)
            .build()
    }
}
