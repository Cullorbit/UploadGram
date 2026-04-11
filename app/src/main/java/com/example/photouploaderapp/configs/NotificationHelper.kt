package com.example.photouploaderapp.configs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.photouploaderapp.MainActivity
import com.example.photouploaderapp.R
import com.example.photouploaderapp.telegrambot.SyncActionReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_sync_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getForegroundNotification(isRunning: Boolean): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIntent = if (isRunning) {
            Intent(context, SyncActionReceiver::class.java).apply { action = "com.example.photouploaderapp.ACTION_STOP" }
        } else {
            Intent(context, SyncActionReceiver::class.java).apply { action = "com.example.photouploaderapp.ACTION_START" }
        }

        val actionPendingIntent = PendingIntent.getBroadcast(
            context, 1, actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionText = if (isRunning) context.getString(R.string.stop_service) else context.getString(R.string.start_service)
        val title = if (isRunning) context.getString(R.string.service_running) else context.getString(R.string.service_stopped_status)
        val message = if (isRunning) context.getString(R.string.service_active_scanning) else context.getString(R.string.service_stopped)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .addAction(0, actionText, actionPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateForegroundNotification(isRunning: Boolean) {
        if (isRunning) {
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, getForegroundNotification(isRunning))
        } else {
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        }
    }

    companion object {
        private const val CHANNEL_ID = "folder_sync_channel"
        private const val NOTIFICATION_ID = 1
        const val FOREGROUND_NOTIFICATION_ID = 101
    }
}
