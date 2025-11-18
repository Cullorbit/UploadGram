package com.example.photouploaderapp.configs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class SyncIntervalDialog(private val settingsManager: SettingsManager) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val intervals = arrayOf("1 минута", "5 минут", "15 минут", "30 минут", "1 час", "2 часа")
        val currentInterval = getIntervalIndex(settingsManager.syncInterval)

        return AlertDialog.Builder(context)
            .setTitle("Интервал синхронизации")
            .setSingleChoiceItems(intervals, currentInterval) { _, which ->
                val interval = getIntervalInMillis(which)
                settingsManager.syncInterval = interval
            }
            .setPositiveButton("OK", null)
            .create()
    }

    private fun getIntervalIndex(interval: Long): Int {
        return when (interval) {
            60 * 1000L -> 0
            5 * 60 * 1000L -> 1
            15 * 60 * 1000L -> 2
            30 * 60 * 1000L -> 3
            60 * 60 * 1000L -> 4
            2 * 60 * 60 * 1000L -> 5
            else -> 4
        }
    }

    private fun getIntervalInMillis(index: Int): Long {
        return when (index) {
            0 -> 60 * 1000L
            1 -> 5 * 60 * 1000L
            2 -> 15 * 60 * 1000L
            3 -> 30 * 60 * 1000L
            4 -> 60 * 60 * 1000L
            5 -> 2 * 60 * 60 * 1000L
            else -> 60 * 60 * 1000L
        }
    }
}