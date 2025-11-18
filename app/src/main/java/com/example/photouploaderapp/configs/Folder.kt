package com.example.photouploaderapp.configs

data class Folder(
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
