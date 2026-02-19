package com.example.photouploaderapp.configs

import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.photouploaderapp.R

class LogHelper(
    private val context: Context,
    private val tvLog: TextView,
    private val scrollViewLog: ScrollView
) {
    private val logPrefs by lazy {
        context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
    }

    fun loadSavedLog() {
        val savedLog = logPrefs.getString("log_content", "")
        if (!savedLog.isNullOrEmpty()) {
            tvLog.text = savedLog
            scrollToBottom()
        }
    }

    fun log(message: String) {
        writeLog(context, message)
    }

    fun clearLog() {
        logPrefs.edit().clear().apply()
        tvLog.text = context.getString(R.string.log_cleared)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollViewLog.post { scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    companion object {
        const val ACTION_LOG_UPDATED = "com.example.photouploaderapp.LOG_UPDATED"

        /**
         * Статический метод для записи лога из любого места (в т.ч. из фоновых воркеров).
         * Записывает сообщение в SharedPreferences и уведомляет UI об обновлении.
         */
        fun writeLog(context: Context, message: String, folderName: String? = null) {
            val fullMessage = if (!folderName.isNullOrEmpty()) "[$folderName] $message" else message
            
            val prefs = context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
            val currentLog = prefs.getString("log_content", "") ?: ""
            
            val prefix = if (currentLog.isEmpty()) "" else "\n\n"
            val newLog = "$currentLog$prefix$fullMessage".trim()
            
            prefs.edit().putString("log_content", newLog).apply()

            val intent = Intent(ACTION_LOG_UPDATED)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}
