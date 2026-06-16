package com.example.billboardanalytics.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "Device booted. Scheduling ScannerService start via WorkManager...");

            // On Android 12+, foreground services cannot be started directly from the
            // background (broadcast receivers). Use WorkManager with a short delay to
            // defer the start into a valid execution window.
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BootStartWorker.class)
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build();

            WorkManager.getInstance(context).enqueue(workRequest);
        }
    }
}
