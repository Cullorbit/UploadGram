package com.example.photouploaderapp.telegrambot

import com.google.gson.annotations.SerializedName

class TelegramResponse {
    @SerializedName("ok")
    var ok: Boolean = false

    @SerializedName("description")
    var description: String? = null
}
