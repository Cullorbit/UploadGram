package com.example.photouploaderapp.configsimport

import android.app.Dialog
import com.example.photouploaderapp.configs.SettingsManager
import com.example.photouploaderapp.configs.UIUpdater
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

class SyncIntervalDialog(private val settingsManager: SettingsManager) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val intervals = arrayOf(
            getString(R.string.fifteen_minutes),
            getString(R.string.thirty_minutes),
            getString(R.string.one_hour),
            getString(R.string.two_hours)
        )
        val currentInterval = getIntervalIndex(settingsManager.syncInterval)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sync_interval))
            .setSingleChoiceItems(intervals, currentInterval) { _, which ->
                val interval = getIntervalInMillis(which)
                settingsManager.syncInterval = interval
                (activity as? MainActivity)?.let {
                    val uiUpdater = UIUpdater(it, settingsManager)
                    uiUpdater.updateSettingsDisplay()
                }
            }
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    private fun getIntervalIndex(interval: Long): Int {
        return when (interval) {
            TimeUnit.MINUTES.toMillis(15) -> 0
            TimeUnit.MINUTES.toMillis(30) -> 1
            TimeUnit.HOURS.toMillis(1) -> 2
            TimeUnit.HOURS.toMillis(2) -> 3
            else -> 0
        }
    }

    private fun getIntervalInMillis(index: Int): Long {
        return when (index) {
            0 -> TimeUnit.MINUTES.toMillis(15)
            1 -> TimeUnit.MINUTES.toMillis(30)
            2 -> TimeUnit.HOURS.toMillis(1)
            3 -> TimeUnit.HOURS.toMillis(2)
            else -> TimeUnit.MINUTES.toMillis(15)
        }
    }
}