package com.example.photouploaderapp.configs

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.example.photouploaderapp.R

object MediaUtils {
    fun isValidMedia(context: Context, file: DocumentFile, mediaType: String): Boolean {
        val fileName = file.name ?: return false
        return isValidMedia(context, fileName, mediaType)
    }

    fun isValidMedia(context: Context, fileName: String, mediaType: String): Boolean {
        val lowerName = fileName.lowercase()
        val isFilePhoto = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { lowerName.endsWith(it) }
        val isFileVideo = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { lowerName.endsWith(it) }
        val isFileAudio = listOf(".mp3", ".m4a", ".ogg", ".wav", ".flac").any { lowerName.endsWith(it) }

        val photoTypeString = context.getString(R.string.only_photo)
        val videoTypeString = context.getString(R.string.only_video)
        val audioTypeString = context.getString(R.string.only_audio)
        val allTypeString = context.getString(R.string.all_media)

        return when (mediaType) {
            photoTypeString -> isFilePhoto
            videoTypeString -> isFileVideo
            audioTypeString -> isFileAudio
            allTypeString -> isFilePhoto || isFileVideo || isFileAudio
            else -> false
        }
    }

    fun isPhoto(file: DocumentFile): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        return listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { fileName.endsWith(it) }
    }

    fun isVideo(file: DocumentFile): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        return listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { fileName.endsWith(it) }
    }

    fun isVideo(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { lowerName.endsWith(it) }
    }
    
    fun isAudio(file: DocumentFile): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        return listOf(".mp3", ".m4a", ".ogg", ".wav", ".flac").any { fileName.endsWith(it) }
    }
}
