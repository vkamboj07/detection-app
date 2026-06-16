package com.example.billboardanalytics.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.billboardanalytics.BuildConfig;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.engine.SessionizationEngine;
import com.example.billboardanalytics.scanner.BluetoothScanner;
import com.example.billboardanalytics.scanner.WiFiScanner;
import com.example.billboardanalytics.sync.SupabaseSyncManager;
import com.example.billboardanalytics.ui.MainActivity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScannerService extends Service {
    private static final String TAG = "ScannerService";
    private static final String CHANNEL_ID = "ScannerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_TRIGGER_SYNC = "com.example.billboardanalytics.ACTION_TRIGGER_SYNC";

    // Heartbeat: automatically sync any unsent data to the cloud every 60 seconds.
    // This covers quiet periods where no new detections occur.
    private static final long HEARTBEAT_INTERVAL_SECONDS = 60;

    private BluetoothScanner bluetoothScanner;
    private WiFiScanner wifiScanner;
    private SessionizationEngine engine;
    private SupabaseSyncManager syncManager;
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatFuture;

    // Guard against double-starting scanners when onStartCommand is called multiple
    // times on the same service instance (START_STICKY restart, boot receiver, etc.)
    private boolean scanningStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScannerService onCreate");

        createNotificationChannel();

        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());

        // Read Supabase credentials from BuildConfig (set in app/build.gradle)
        String supabaseUrl  = BuildConfig.SUPABASE_URL;
        String supabaseKey  = BuildConfig.SUPABASE_ANON_KEY;
        syncManager = new SupabaseSyncManager(
                getApplicationContext(), db.analyticsDao(), supabaseUrl, supabaseKey);

        engine = new SessionizationEngine(db.analyticsDao(), syncManager);

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

        if (intent != null) {
            if (ACTION_TRIGGER_SYNC.equals(intent.getAction())) {
                Log.d(TAG, "Sync action triggered via Intent");
                if (syncManager != null) {
                    syncManager.syncImmediately();
                }
                return START_STICKY;
            }
        }

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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION |
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            if (!scanningStarted) {
                scanningStarted = true;
                bluetoothScanner.startScanning();
                wifiScanner.startScanning();

                // Start periodic heartbeat — automatically pushes any unsent rows to the
                // cloud every HEARTBEAT_INTERVAL_SECONDS even when scanning is quiet.
                startHeartbeat();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground or scanners: " + e.getMessage());
        }

        // START_STICKY ensures the OS restarts the service if it gets killed for memory
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ScannerService onDestroy");

        stopHeartbeat();

        try {
            stopForeground(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground: " + e.getMessage());
        }

        try {
            if (bluetoothScanner != null) bluetoothScanner.stopScanning();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Bluetooth scanner: " + e.getMessage());
        }

        try {
            if (wifiScanner != null) wifiScanner.stopScanning();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping Wi-Fi scanner: " + e.getMessage());
        }

        try {
            if (engine != null) engine.shutdown();
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down engine: " + e.getMessage());
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) return;
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatFuture = heartbeatScheduler.scheduleWithFixedDelay(() -> {
            if (syncManager != null) {
                Log.d(TAG, "Heartbeat: triggering automatic background sync.");
                syncManager.syncAsync();
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
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
