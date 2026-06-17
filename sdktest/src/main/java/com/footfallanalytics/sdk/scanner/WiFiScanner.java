package com.footfallanalytics.sdk.scanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.footfallanalytics.sdk.model.Observation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class WiFiScanner {
    private static final String TAG = "FSDK_WiFiScanner";

    private final WifiManager wifiManager;
    private final ObservationCallback callback;
    private final long pollIntervalMs;

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    private volatile boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();

    public interface ObservationCallback {
        void onObservationDetected(Observation observation);
    }

    public WiFiScanner(Context context, ObservationCallback callback, long pollIntervalMs) {
        this.callback = callback;
        this.pollIntervalMs = pollIntervalMs;
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    public void startScanning() {
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi unavailable or disabled");
            return;
        }
        isScanning = true;
        handler.post(this::pollResults);
    }

    public void stopScanning() {
        isScanning = false;
        handler.removeCallbacksAndMessages(null);
        scanExecutor.shutdownNow();
    }

    private void pollResults() {
        if (!isScanning) return;
        scanExecutor.execute(() -> {
            readCachedResults();
            if (isScanning) {
                handler.postDelayed(this::pollResults, pollIntervalMs);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void readCachedResults() {
        try {
            try { wifiManager.startScan(); } catch (Exception ignored) {}

            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null || results.isEmpty()) return;

            for (ScanResult result : results) {
                if (result.BSSID == null || result.BSSID.isEmpty()) continue;
                Observation obs = new Observation("WIFI", result.BSSID, result.level, getCurrentTimestamp());
                obs.setBssid(result.BSSID);
                obs.setSsid(result.SSID);
                obs.setFrequency(result.frequency);
                if (callback != null) callback.onObservationDetected(obs);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing location permission for Wi-Fi scanning", e);
        }
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }
}
