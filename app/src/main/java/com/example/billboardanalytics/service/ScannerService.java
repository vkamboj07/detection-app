package com.example.billboardanalytics.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.engine.SessionizationEngine;
import com.example.billboardanalytics.scanner.BluetoothScanner;
import com.example.billboardanalytics.scanner.WiFiScanner;
import com.example.billboardanalytics.ui.MainActivity;

public class ScannerService extends Service {
    private static final String TAG = "ScannerService";
    private static final String CHANNEL_ID = "ScannerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private BluetoothScanner bluetoothScanner;
    private WiFiScanner wifiScanner;
    private SessionizationEngine engine;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScannerService onCreate");

        createNotificationChannel();

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        engine = new SessionizationEngine(db.analyticsDao());

        bluetoothScanner = new BluetoothScanner(this, observation -> 
            engine.processDetection(observation.getMac(), observation.getSource(), observation.getRssi())
        );

        wifiScanner = new WiFiScanner(this, observation -> 
            engine.processDetection(observation.getBssid(), observation.getSource(), observation.getRssi())
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScannerService onStartCommand");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Billboard Analytics")
                .setContentText("Scanning for nearby devices in the background...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification, 
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | 
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

        try {
            bluetoothScanner.startScanning();
            wifiScanner.startScanning();
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permissions to start scanners: " + e.getMessage());
        }

        // START_STICKY ensures the OS restarts the service if it gets killed for memory
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ScannerService onDestroy");
        
        if (bluetoothScanner != null) bluetoothScanner.stopScanning();
        if (wifiScanner != null) wifiScanner.stopScanning();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Background Scanner Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Keeps the Billboard Analytics scanner running.");
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
