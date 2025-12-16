package com.example.photouploaderapp.configs

import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import com.example.photouploaderapp.R

class LogHelper(
    private val context: Context,
    private val tvLog: TextView,
    private val scrollViewLog: ScrollView
) {
    private val logPrefs by lazy {
        context.getSharedPreferences("AppLog", Context.MODE_PRIVATE)
    }

    fun clearSavedLog() {
        logPrefs.edit().clear().apply()
        tvLog.text = ""
    }

    fun log(message: String) {
        val currentLog = tvLog.text.toString()
        val newLog = "$currentLog\n\n$message"
        tvLog.text = newLog.trim()
        logPrefs.edit().putString("log_content", newLog).apply()
        scrollToBottom()
    }

    fun clearLog() {
        tvLog.text = context.getString(R.string.log_cleared)
        logPrefs.edit().clear().apply()
    }

    private fun scrollToBottom() {
        scrollViewLog.post { scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
