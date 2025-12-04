package com.example.photouploaderapp.configs

import java.util.UUID

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val topic: String,
    val mediaType: String,
    val path: String,
    var isSyncing: Boolean = false,
    var botToken: String = "",
    var chatId: String = "",
    var isTopicEnabled: Boolean = false
) {
    fun getTopicId(): Int? {
        return topic.toIntOrNull()
    }
}
