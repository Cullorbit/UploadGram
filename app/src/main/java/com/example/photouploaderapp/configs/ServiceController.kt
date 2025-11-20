package com.example.photouploaderapp.configs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.photouploaderapp.telegrambot.UploadService
import com.example.photouploaderapp.telegrambot.UploadWorker
import java.util.concurrent.TimeUnit
import com.example.photouploaderapp.R

class ServiceController(private val context: Context, private val settingsManager: SettingsManager) {

    private var isServiceActive = false
    private val workManager = WorkManager.getInstance(context)
    fun startService(folders: List<Folder>) {
        stopServiceInternal()

        folders.filter { it.isSyncing }.forEach { folder ->
            if (!validateFolder(folder)) return@forEach

            context.startService(createServiceIntent(folder).apply {
                action = "FOLDER_${folder.name.hashCode()}"
            })
        }
        isServiceActive = true
    }


    fun schedulePeriodicUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWork = PeriodicWorkRequestBuilder<UploadWorker>(
            15, // Интервал 15 минут
            TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            "telegramUploadWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            uploadWork
        )
    }

    fun cancelUpload() {
        workManager.cancelUniqueWork("telegramUploadWork")
    }

    private fun createServiceIntent(folder: Folder): Intent {
        return Intent(context, UploadService::class.java).apply {
            putExtra("KEY_BOT_TOKEN", folder.botToken.ifEmpty { settingsManager.botToken ?: "" })
            putExtra("KEY_CHAT", folder.chatId.ifEmpty { settingsManager.chatId ?: "" })
            putExtra("KEY_MEDIA_TYPE", folder.mediaType)
            putExtra("KEY_SYNC_FOLDER", folder.path)
            putExtra("KEY_TOPIC", folder.getTopicId() ?: -1)
            putExtra("KEY_FOLDER_NAME", folder.name)
        }
    }

    private fun validateFolder(folder: Folder): Boolean {
        if (folder.botToken.isEmpty() && settingsManager.botToken.isNullOrEmpty()) {
            showToast(context.getString(R.string.configure_bot_token_for_folder, folder.name))
            return false
        }
        if (folder.path.isEmpty()) {
            showToast(context.getString(R.string.select_folder_for, folder.name))
            return false
        }
        return true
    }

    @SuppressLint("ImplicitSamInstance")
    fun stopService() {
        stopServiceInternal()
        if(isServiceActive()) return
        showToast(context.getString(R.string.service_stopped))
    }

    private fun stopServiceInternal() {
        if (!isServiceActive) return
        context.stopService(Intent(context, UploadService::class.java))
        isServiceActive = false
    }

    fun isServiceActive(): Boolean = isServiceActive

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
