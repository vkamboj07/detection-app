package com.footfallanalytics.sdk.service;

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

import com.footfallanalytics.sdk.data.AnalyticsDao;
import com.footfallanalytics.sdk.data.AppDatabase;
import com.footfallanalytics.sdk.engine.SessionizationEngine;
import com.footfallanalytics.sdk.scanner.BluetoothScanner;
import com.footfallanalytics.sdk.scanner.WiFiScanner;
import com.footfallanalytics.sdk.sync.SupabaseSyncManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScannerService extends Service {
    private static final String TAG = "FSDK_ScannerService";
    private static final String CHANNEL_ID = "FootfallSDKChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START = "com.footfallanalytics.sdk.action.START_SCANNING";
    public static final String ACTION_STOP = "com.footfallanalytics.sdk.action.STOP_SCANNING";
    public static final String EXTRA_SUPABASE_URL = "supabase_url";
    public static final String EXTRA_SUPABASE_KEY = "supabase_anon_key";
    public static final String EXTRA_SESSION_TIMEOUT = "session_timeout_ms";
    public static final String EXTRA_WIFI_POLL_INTERVAL = "wifi_poll_interval_ms";
    public static final String EXTRA_CLASSIC_BT = "classic_bt_enabled";

    private static final long HEARTBEAT_INTERVAL_SECONDS = 60;

    private BluetoothScanner bluetoothScanner;
    private WiFiScanner wifiScanner;
    private SessionizationEngine sessionEngine;
    private SupabaseSyncManager syncManager;
    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatFuture;

    private boolean scanningStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScannerService onCreate");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScannerService onStartCommand");

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopScanningAndSelf();
            return START_NOT_STICKY;
        }

        String supabaseUrl = intent.getStringExtra(EXTRA_SUPABASE_URL);
        String supabaseKey = intent.getStringExtra(EXTRA_SUPABASE_KEY);
        long sessionTimeout = intent.getLongExtra(EXTRA_SESSION_TIMEOUT, 600_000L);
        long wifiPollInterval = intent.getLongExtra(EXTRA_WIFI_POLL_INTERVAL, 30_000L);
        boolean classicBt = intent.getBooleanExtra(EXTRA_CLASSIC_BT, false);

        if (supabaseUrl == null || supabaseKey == null) {
            Log.e(TAG, "Missing Supabase credentials in intent extras");
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground();

        if (!scanningStarted) {
            initializeScanners(supabaseUrl, supabaseKey, sessionTimeout, wifiPollInterval, classicBt);
            scanningStarted = true;
            bluetoothScanner.startScanning();
            wifiScanner.startScanning();
            startHeartbeat();
            Log.i(TAG, "SDK background scanning started");
        }

        return START_STICKY;
    }

    private void initializeScanners(String supabaseUrl, String supabaseKey,
                                     long sessionTimeout, long wifiPollInterval,
                                     boolean classicBt) {
        AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
        AnalyticsDao dao = db.analyticsDao();

        syncManager = new SupabaseSyncManager(
                getApplicationContext(), dao, supabaseUrl, supabaseKey);

        sessionEngine = new SessionizationEngine(
                dao, syncManager, sessionTimeout,
                new SessionizationEngine.EngineCallback() {
                    @Override
                    public void onSessionCreated(long deviceId, long sessionId, long duration) {
                        Log.d(TAG, "Session created: device=" + deviceId);
                    }

                    @Override
                    public void onDetectionProcessed() {}
                }
        );

        bluetoothScanner = new BluetoothScanner(
                getApplicationContext(),
                observation -> sessionEngine.processDetection(
                        observation.getMac(), observation.getSource(), observation.getRssi()
                ),
                classicBt
        );

        wifiScanner = new WiFiScanner(
                getApplicationContext(),
                observation -> sessionEngine.processDetection(
                        observation.getBssid(), observation.getSource(), observation.getRssi()
                ),
                wifiPollInterval
        );
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "ScannerService onDestroy");
        stopHeartbeat();

        try {
            if (bluetoothScanner != null) bluetoothScanner.stopScanning();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping BT scanner", e);
        }

        try {
            if (wifiScanner != null) wifiScanner.stopScanning();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping WiFi scanner", e);
        }

        try {
            if (sessionEngine != null) sessionEngine.shutdown();
        } catch (Exception e) {
            Log.e(TAG, "Error shutting down engine", e);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground", e);
        }

        scanningStarted = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopScanningAndSelf() {
        stopHeartbeat();

        try {
            if (bluetoothScanner != null) bluetoothScanner.stopScanning();
        } catch (Exception ignored) {}

        try {
            if (wifiScanner != null) wifiScanner.stopScanning();
        } catch (Exception ignored) {}

        try {
            if (sessionEngine != null) sessionEngine.shutdown();
        } catch (Exception ignored) {}

        scanningStarted = false;
        stopSelf();
    }

    private void ensureForeground() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Footfall Analytics")
                .setContentText("Scanning for nearby devices...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
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
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) return;
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatFuture = heartbeatScheduler.scheduleWithFixedDelay(() -> {
            if (syncManager != null) {
                Log.d(TAG, "Heartbeat: triggering background sync.");
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
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Footfall Scanner",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps the footfall analytics scanner running in the background.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
