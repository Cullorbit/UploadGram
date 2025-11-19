package com.example.photouploaderapp.telegrambot;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.util.Log;

@SuppressLint("SpecifyJobSchedulerIdRange")
public class MediaSyncJob extends JobService {
    private static final String TAG = "MediaSyncJob";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Starting MediaSyncJob");
        Intent serviceIntent = new Intent(this, (Class<?>) UploadService.class);
        startService(serviceIntent);
        return false;
    }

    @SuppressLint("SpecifyJobSchedulerIdRange")
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Stopping MediaSyncJob");
        return true;
    }
}