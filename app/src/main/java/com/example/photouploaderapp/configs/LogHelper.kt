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
    private var tvLog: TextView? = null,
    private var scrollViewLog: ScrollView? = null
) {
    private val logPrefs by lazy {
        context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
    }

    fun updateViews(tvLog: TextView, scrollViewLog: ScrollView) {
        this.tvLog = tvLog
        this.scrollViewLog = scrollViewLog
        loadSavedLog()
    }

    fun loadSavedLog() {
        val savedLog = logPrefs.getString("log_content", "")
        tvLog?.text = if (savedLog.isNullOrEmpty()) "" else savedLog
        scrollToBottom()
    }

    fun appendLog(message: String) {
        tvLog?.let { tv ->
            val currentText = tv.text.toString()
            val prefix = if (currentText.isEmpty()) "" else "\n\n"
            tv.append("$prefix$message")
            scrollToBottom()
        }
    }

    fun log(message: String) {
        writeLog(context, message)
    }

    fun clearLog() {
        logPrefs.edit().clear().apply()
        tvLog?.text = context.getString(R.string.log_cleared)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        scrollViewLog?.post { scrollViewLog?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    companion object {
        const val ACTION_LOG_UPDATED = "com.example.photouploaderapp.LOG_UPDATED"
        const val EXTRA_MESSAGE = "extra_log_message"

        @Synchronized
        fun writeLog(context: Context, message: String, folderName: String? = null) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val folderPrefix = if (!folderName.isNullOrEmpty()) "[$folderName] " else ""
            val fullMessage = "[$time] $folderPrefix$message"
            
            val prefs = context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
            val currentLog = prefs.getString("log_content", "") ?: ""
            
            val prefix = if (currentLog.isEmpty()) "" else "\n\n"
            val newLog = "$currentLog$prefix$fullMessage".trim()
            
            val maxLogSize = 30000 
            val truncatedLog = if (newLog.length > maxLogSize) {
                "..." + newLog.takeLast(maxLogSize - 3)
            } else {
                newLog
            }

            prefs.edit().putString("log_content", truncatedLog).apply()

            val intent = Intent(ACTION_LOG_UPDATED).apply {
                putExtra(EXTRA_MESSAGE, fullMessage)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}
