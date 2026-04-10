package com.example.photouploaderapp.configs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

class SyncIntervalDialog(private val settingsManager: SettingsManager) : DialogFragment() {

    private var tempInterval: Long = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val intervals = arrayOf(
            getString(R.string.fifteen_minutes),
            getString(R.string.thirty_minutes),
            getString(R.string.one_hour),
            getString(R.string.two_hours)
        )
        tempInterval = settingsManager.syncInterval
        val currentIntervalIndex = getIntervalIndex(tempInterval)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sync_interval))
            .setSingleChoiceItems(intervals, currentIntervalIndex) { _, which ->
                tempInterval = getIntervalInMillis(which)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                if (settingsManager.syncInterval != tempInterval) {
                    settingsManager.syncInterval = tempInterval
                    (activity as? MainActivity)?.let {
                        UIUpdater(it, settingsManager).updateSettingsDisplay()
                        it.stopServiceIfNeeded()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
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
