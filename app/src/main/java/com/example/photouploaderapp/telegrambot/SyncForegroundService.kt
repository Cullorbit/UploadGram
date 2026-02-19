package com.example.photouploaderapp.telegrambot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.photouploaderapp.configs.NotificationHelper

class SyncForegroundService : Service() {

    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isRunning = intent?.getBooleanExtra("is_running", false) ?: false
        
        if (isRunning) {
            startForeground(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notificationHelper.getForegroundNotification(true)
            )
        } else {
            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
