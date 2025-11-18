package com.example.photouploaderapp.telegrambot

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val intent = Intent(applicationContext, UploadService::class.java)
        applicationContext.startService(intent)
        return Result.success()
    }
}