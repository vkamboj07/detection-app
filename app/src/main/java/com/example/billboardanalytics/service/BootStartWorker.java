package com.example.billboardanalytics.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
        // Only restart the service if the user had tracking active before the reboot.
        // Without this check the scanner would auto-start on every boot even if the
        // user had explicitly stopped it.
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE);
        boolean wasTracking = prefs.getBoolean("is_tracking", false);

        if (!wasTracking) {
            Log.d(TAG, "Tracking was not active before reboot — skipping service start.");
            return Result.success();
        }

        Log.d(TAG, "Tracking was active before reboot — restarting ScannerService.");
        Intent serviceIntent = new Intent(getApplicationContext(), ScannerService.class);
        try {
            getApplicationContext().startForegroundService(serviceIntent);
            return Result.success();
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            Log.e(TAG, "Android 12+ restriction: cannot start foreground service from background. " +
                    "Consider using an app-whitelist or scheduling via a user-visible action.", e);
            return Result.failure();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ScannerService from WorkManager: " + e.getMessage());
            return Result.retry();
        }
    }
}
