package com.example.photouploaderapp.configs

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LogHelper(context: Context) {

    private val logFile = File(context.filesDir, "sync_log.txt")

    fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "$timestamp: $message\n"
        FileOutputStream(logFile, true).bufferedWriter().use {
            it.write(logMessage)
        }
    }

    fun getLog(): String {
        return try {
            logFile.readText()
        } catch (e: FileNotFoundException) {
            "" // Пустая строка, если файл не найден
        }
    }

    fun clearLog() {
        logFile.writeText("")
    }
}