package com.example.photouploaderapp.configs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.R

class SyncIntervalDialog(private val settingsManager: SettingsManager) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val intervals = arrayOf(getString(R.string.one_minute), getString(R.string.five_minutes), getString(R.string.fifteen_minutes), getString(R.string.thirty_minutes), getString(R.string.one_hour), getString(R.string.two_hours))
        val currentInterval = getIntervalIndex(settingsManager.syncInterval)

        return AlertDialog.Builder(context)
            .setTitle(getString(R.string.sync_interval))
            .setSingleChoiceItems(intervals, currentInterval) { _, which ->
                val interval = getIntervalInMillis(which)
                settingsManager.syncInterval = interval
            }
            .setPositiveButton(getString(R.string.ok), null)
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