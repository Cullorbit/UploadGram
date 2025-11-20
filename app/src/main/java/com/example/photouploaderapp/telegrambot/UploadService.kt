package com.example.photouploaderapp.telegrambot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.photouploaderapp.configs.SettingsManager
import androidx.core.net.toUri
import androidx.core.content.edit
import java.util.LinkedList
import java.util.Queue
import com.example.photouploaderapp.R

class UploadService : Service() {

    private data class UploadTask(
        val file: DocumentFile,
        val folderName: String,
        val mediaType: String,
        val topicId: Int?
    )
    private val TAG = "UploadService"

    private lateinit var botToken: String
    private lateinit var chatId: String
    private var syncFolderUri: Uri? = null
    private var topicId: Int? = null
    private var currentMediaType: String = ""
    private var folderName: String = ""
    private var currentStartId: Int = 0

    private lateinit var telegramBot: TelegramBot
    private lateinit var handler: Handler
    private val uploadQueue: Queue<UploadTask> = LinkedList()
    private val queueLock = Any()
    private var isProcessing = false
    private val delayBetweenUploads = 2000L

    private val sentFilesPrefs by lazy { getSharedPreferences("SentFiles", Context.MODE_PRIVATE) }
    private val settingsManager: SettingsManager by lazy { SettingsManager(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        botToken = intent?.getStringExtra("KEY_BOT_TOKEN") ?: ""
        chatId = intent?.getStringExtra("KEY_CHAT") ?: ""
        val syncFolderStr = intent?.getStringExtra("KEY_SYNC_FOLDER")
        topicId = intent?.getIntExtra("KEY_TOPIC", -1)?.takeIf { it > 0 }
        currentMediaType = intent?.getStringExtra("KEY_MEDIA_TYPE") ?: ""
        folderName = intent?.getStringExtra("KEY_FOLDER_NAME") ?: ""

        handler = Handler(Looper.getMainLooper())
        currentStartId = startId

        if (intent?.action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (botToken.isEmpty() || chatId.isEmpty()) {
            sendLog(getString(R.string.settings_missing_stopping_service))
            stopSelf()
            return START_NOT_STICKY
        }

        // Вывод списка сохранённых разрешений для отладки
        contentResolver.persistedUriPermissions.forEach {
            Log.d(TAG, "Persisted URI: ${it.uri}, writePermission: ${it.isWritePermission}")
        }

        // Если URI выбранной папки есть, проверяем наличие разрешения
        if (!syncFolderStr.isNullOrEmpty()) {
            syncFolderUri = syncFolderStr.toUri()
        }

        if (syncFolderUri != null) {
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri.toString() == syncFolderUri.toString() && it.isWritePermission
            }
            if (!hasPermission) {
                sendLog(getString(R.string.no_folder_access_reselect))
                try {
                    contentResolver.takePersistableUriPermission(
                        syncFolderUri!!,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    sendLog(getString(R.string.permission_restore_attempted))
                } catch (e: Exception) {
                    Log.e(TAG, getString(R.string.error_restoring_permission), e)
                }
            }
        }

        telegramBot = TelegramBot(botToken, chatId, this, topicId)
        sendLog(getString(R.string.service_started_with_config, botToken, chatId, topicId.toString()))

        startPeriodicCheck()
        uploadMediaFiles()

        handler = Handler(Looper.getMainLooper())
        startProcessingQueue()
        return START_STICKY
    }

    private fun startProcessingQueue() {
        handler.post(object : Runnable {
            override fun run() {
                synchronized(queueLock) {
                    if (isProcessing) return

                    val file = uploadQueue.poll()
                    if (file != null) {
                        isProcessing = true
                        processFile(file)
                    }
                }

                handler.postDelayed(this, 500)
            }
        })
    }

    private fun processFile(task: UploadTask) {
        val fileName = task.file.name ?: return

        telegramBot.sendDocument(task.file, task.topicId) { success ->
            synchronized(queueLock) {
                isProcessing = false

                if (success) {
                    markFileAsSent(fileName, task.folderName, task.mediaType)
                    sendLog(getString(R.string.file_sent_successfully, fileName))
                } else {
                    uploadQueue.offer(task)
                    sendLog(getString(R.string.error_sending_file_queued, fileName))
                }
            }

            handler.postDelayed({ startProcessingQueue() }, delayBetweenUploads)
        }
    }

    private fun shouldProcessFile(docFile: DocumentFile, folderName: String, mediaType: String): Boolean {
        val fileName = docFile.name ?: return false
        return docFile.isFile &&
                !isFileSent(fileName, folderName, mediaType) &&
                isValidMedia(docFile)
    }



    private fun startPeriodicCheck() {
        val interval = settingsManager.syncInterval * 60000

        handler.postDelayed(object : Runnable {
            override fun run() {
                uploadMediaFiles()
                handler.postDelayed(this, interval)
            }
        }, interval)
    }

    private var isAlive = true

    override fun onDestroy() {
        super.onDestroy()
        isAlive = false
        handler.removeCallbacksAndMessages(null)
        synchronized(queueLock) {
            uploadQueue.clear()
        }
    }

    private fun uploadMediaFiles() {
        sendLog(getString(R.string.starting_file_upload))

        syncFolderUri?.let { uri ->
            val documentFolder = DocumentFile.fromTreeUri(this, uri)

            documentFolder?.listFiles()?.forEach { docFile ->
                if (shouldProcessFile(docFile, folderName, currentMediaType)) {
                    synchronized(queueLock) {
                        val task = UploadTask(
                            file = docFile,
                            folderName = this.folderName, // Берем из свойств сервиса
                            mediaType = this.currentMediaType,
                            topicId = this.topicId
                        )
                        uploadQueue.offer(task)
                    }
                    sendLog(getString(R.string.file_added_to_queue, docFile.name))
                }
            }
        }

            handler.removeCallbacksAndMessages(null)
    }

    private fun isValidMedia(file: DocumentFile): Boolean {
        val fileName = file.name?.lowercase() ?: return false

        val isPhoto = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") || fileName.endsWith(".gif")
        val isVideo = fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                fileName.endsWith(".mkv") || fileName.endsWith(".mov")

        return when(currentMediaType.lowercase()) {
            getString(R.string.only_photo).lowercase(), "photo" -> isPhoto
            getString(R.string.only_video).lowercase(), "video" -> isVideo
            getString(R.string.all_media).lowercase(), "all" -> isPhoto || isVideo
            else -> false
        }
    }

    private fun sendLog(message: String) {
        Log.d(TAG, message)
        val intent = Intent("com.example.photouploaderapp.UPLOAD_LOG").apply {
            putExtra("log_message", message)
            putExtra("folder_name", folderName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun isFileSent(fileName: String, folderName: String, mediaType: String): Boolean {
        return sentFilesPrefs.contains("${folderName}_${mediaType}_$fileName")
    }

    private fun markFileAsSent(fileName: String, folderName: String, mediaType: String) {
        sentFilesPrefs.edit {
            putBoolean("${folderName}_${mediaType}_$fileName", true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isServiceRunning: Boolean = false

        fun clearSentFilesForFolder(context: Context, folderName: String, originalMediaType: String) {
            val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
            sentFilesPrefs.edit {
                sentFilesPrefs.all.keys
                    .filter { it.startsWith("${folderName}_${originalMediaType}_") }
                    .forEach { remove(it) }
                apply()
            }
        }
    }
}
