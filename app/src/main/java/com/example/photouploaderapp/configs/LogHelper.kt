package com.example.photouploaderapp.configs

import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.photouploaderapp.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        tvLog.text = if (savedLog.isNullOrEmpty()) "" else savedLog
        scrollToBottom()
    }

    /**
     * Добавляет одну строку в UI без полной перезагрузки всего лога из SharedPreferences.
     * Это решает проблему "зависания" интерфейса при большом количестве сообщений.
     */
    fun appendLog(message: String) {
        val currentText = tvLog.text.toString()
        val prefix = if (currentText.isEmpty()) "" else "\n\n"
        tvLog.append("$prefix$message")
        scrollToBottom()
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
        const val EXTRA_MESSAGE = "extra_log_message"

        /**
         * Статический метод для записи лога.
         * Использует synchronized для предотвращения конфликтов записи.
         */
        @Synchronized
        fun writeLog(context: Context, message: String, folderName: String? = null) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val folderPrefix = if (!folderName.isNullOrEmpty()) "[$folderName] " else ""
            val fullMessage = "[$time] $folderPrefix$message"
            
            val prefs = context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
            val currentLog = prefs.getString("log_content", "") ?: ""
            
            val prefix = if (currentLog.isEmpty()) "" else "\n\n"
            val newLog = "$currentLog$prefix$fullMessage".trim()
            
            // Ограничиваем общий размер лога в памяти
            val maxLogSize = 30000 
            val truncatedLog = if (newLog.length > maxLogSize) {
                "..." + newLog.takeLast(maxLogSize - 3)
            } else {
                newLog
            }

            prefs.edit().putString("log_content", truncatedLog).apply()

            // Отправляем сообщение вместе с интентом, чтобы UI мог просто добавить его
            val intent = Intent(ACTION_LOG_UPDATED).apply {
                putExtra(EXTRA_MESSAGE, fullMessage)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}
