package com.example.billboardanalytics.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "Device booted. Starting ScannerService...");

            Intent serviceIntent = new Intent(context, ScannerService.class);

            try {
                context.startForegroundService(serviceIntent);
            } catch (Exception e) {
                // ForegroundServiceStartNotAllowedException on Android 12+ in rare cases
                Log.e(TAG, "Failed to start ScannerService from boot: " + e.getMessage());
            }
        }
    }
}
