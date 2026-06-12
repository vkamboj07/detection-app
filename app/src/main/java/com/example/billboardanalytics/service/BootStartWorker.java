package com.example.billboardanalytics.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BootStartWorker extends Worker {

    private static final String TAG = "BootStartWorker";

    public BootStartWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting ScannerService from WorkManager...");
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), ScannerService.class);
            getApplicationContext().startForegroundService(serviceIntent);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScannerService from WorkManager: " + e.getMessage());
            return Result.retry();
        }
    }
}
