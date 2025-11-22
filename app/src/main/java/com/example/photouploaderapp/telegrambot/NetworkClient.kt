package com.example.photouploaderapp.telegrambot

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    // Увеличиваем таймауты, т.к. бесплатный сервер на Render "засыпает"
    // и первый запрос может быть долгим. 2 минуты - хороший запас.
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(15, TimeUnit.MINUTES) // 15 минут на загрузку большого файла
            .readTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}
        