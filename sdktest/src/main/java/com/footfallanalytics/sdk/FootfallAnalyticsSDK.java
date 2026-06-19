package com.footfallanalytics.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.footfallanalytics.sdk.data.AnalyticsDao;
import com.footfallanalytics.sdk.data.AppDatabase;
import com.footfallanalytics.sdk.engine.AnalyticsEngine;
import com.footfallanalytics.sdk.engine.SessionizationEngine;
import com.footfallanalytics.sdk.model.FootfallMetrics;
import com.footfallanalytics.sdk.scanner.BluetoothScanner;
import com.footfallanalytics.sdk.scanner.WiFiScanner;
import com.footfallanalytics.sdk.sync.SupabaseSyncManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class FootfallAnalyticsSDK {

    private static final String TAG = "FootfallAnalyticsSDK";
    private static volatile FootfallAnalyticsSDK instance;

    private Context appContext;
    private SDKConfig config;
    private FootfallListener listener;

    private AppDatabase database;
    private AnalyticsDao dao;
    private AnalyticsEngine analyticsEngine;
    private SessionizationEngine sessionEngine;
    private SupabaseSyncManager syncManager;
    private BluetoothScanner bluetoothScanner;
    private WiFiScanner wifiScanner;

    private boolean initialized = false;
    private boolean scanning = false;

    private ScheduledExecutorService metricsScheduler;
    private ScheduledFuture<?> metricsFuture;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FootfallAnalyticsSDK() {}

    public static FootfallAnalyticsSDK getInstance() {
        if (instance == null) {
            synchronized (FootfallAnalyticsSDK.class) {
                if (instance == null) {
                    instance = new FootfallAnalyticsSDK();
                }
            }
        }
        return instance;
    }

    public void initialize(Context context, SDKConfig sdkConfig) {
        if (initialized) {
            Log.w(TAG, "SDK already initialized. Call shutdown() first to re-initialize.");
            return;
        }
        this.appContext = context.getApplicationContext();
        this.config = sdkConfig;

        this.metricsScheduler = Executors.newSingleThreadScheduledExecutor();

        this.database = AppDatabase.getDatabase(appContext);
        this.dao = database.analyticsDao();

        this.syncManager = new SupabaseSyncManager(
                appContext, dao,
                sdkConfig.getSupabaseUrl(),
                sdkConfig.getSupabaseAnonKey()
        );

        this.sessionEngine = new SessionizationEngine(
                dao, syncManager, sdkConfig.getSessionTimeoutMs(),
                new SessionizationEngine.EngineCallback() {
                    @Override
                    public void onSessionCreated(long deviceId, long sessionId, long duration) {
                        Log.d(TAG, "Session created: device=" + deviceId + " session=" + sessionId);
                    }

                    @Override
                    public void onDetectionProcessed() {}
                }
        );

        this.analyticsEngine = new AnalyticsEngine(dao);

        this.bluetoothScanner = new BluetoothScanner(
                appContext,
                observation -> {
                    sessionEngine.processDetection(
                            observation.getMac(), observation.getSource(), observation.getRssi()
                    );
                    notifyOnMainThread(() -> {
                        if (listener != null) listener.onObservationDetected(observation);
                    });
                },
                sdkConfig.isClassicBtScanningEnabled()
        );

        this.wifiScanner = new WiFiScanner(
                appContext,
                observation -> {
                    sessionEngine.processDetection(
                            observation.getBssid(), observation.getSource(), observation.getRssi()
                    );
                    notifyOnMainThread(() -> {
                        if (listener != null) listener.onObservationDetected(observation);
                    });
                },
                sdkConfig.getWifiPollIntervalMs()
        );

        this.initialized = true;
        Log.i(TAG, "FootfallAnalyticsSDK initialized successfully");
    }

    public void setListener(FootfallListener listener) {
        this.listener = listener;
    }

    public void startScanning() {
        checkInitialized();
        if (scanning) {
            Log.w(TAG, "Scanning is already active");
            return;
        }
        scanning = true;
        bluetoothScanner.startScanning();
        wifiScanner.startScanning();
        startMetricsPolling();
        notifyOnMainThread(() -> {
            if (listener != null) listener.onScanningStarted();
        });
        Log.i(TAG, "Scanning started");
    }

    public void stopScanning() {
        if (!scanning) return;
        scanning = false;
        stopMetricsPolling();
        bluetoothScanner.stopScanning();
        wifiScanner.stopScanning();
        notifyOnMainThread(() -> {
            if (listener != null) listener.onScanningStopped();
        });
        Log.i(TAG, "Scanning stopped");
    }

    public boolean isScanning() {
        return scanning;
    }

    public FootfallMetrics getMetrics() {
        checkInitialized();
        return analyticsEngine.generateMetricsForToday();
    }

    public void triggerSync() {
        checkInitialized();
        if (syncManager != null) {
            syncManager.syncImmediately();
        }
    }

    public void shutdown() {
        if (!initialized) return;
        stopScanning();
        stopMetricsPolling();
        if (sessionEngine != null) sessionEngine.shutdown();
        if (metricsScheduler != null) metricsScheduler.shutdownNow();
        instance = null;
        initialized = false;
        Log.i(TAG, "FootfallAnalyticsSDK shut down");
    }

    private void startMetricsPolling() {
        if (metricsFuture != null) return;
        metricsFuture = metricsScheduler.scheduleWithFixedDelay(() -> {
            try {
                FootfallMetrics metrics = analyticsEngine.generateMetricsForToday();
                notifyOnMainThread(() -> {
                    if (listener != null) listener.onMetricsUpdated(metrics);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error computing metrics", e);
                notifyOnMainThread(() -> {
                    if (listener != null) listener.onError("Metrics computation failed", e);
                });
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void stopMetricsPolling() {
        if (metricsFuture != null) {
            metricsFuture.cancel(false);
            metricsFuture = null;
        }
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "FootfallAnalyticsSDK is not initialized. Call initialize() first.");
        }
    }

    private void notifyOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
