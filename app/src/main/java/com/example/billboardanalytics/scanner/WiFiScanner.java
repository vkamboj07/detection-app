package com.example.billboardanalytics.scanner;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.billboardanalytics.data.Observation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Scans for nearby Wi-Fi access points and reports each one as an Observation.
 * WHY NO startScan():
 * WifiManager.startScan() is severely throttled on Android 9+ and returns false
 * nearly 100% of the time when called from a background service on API 30+
 * (which is this app's minSdk). The OS continues to refresh the scan cache
 * independently via the system Wi-Fi scanner. We simply poll getScanResults()
 * on a fixed interval to read that cache — no startScan() required.
 */
@SuppressWarnings("deprecation")
public class WiFiScanner {
    private static final String TAG = "WiFiScanner";

    // Poll the OS cached scan results every 15 seconds.
    // The OS refreshes the cache on its own schedule (typically every ~20-30s).
    private static final long POLL_INTERVAL_MS = 15_000;

    private final WifiManager wifiManager;
    private final ObservationCallback callback;

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Background executor for the actual getScanResults() Binder call so we
    // don't block the main thread during the brief IPC to the Wi-Fi service.
    private final java.util.concurrent.ExecutorService scanExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    public interface ObservationCallback {
        void onObservationDetected(Observation observation);
    }

    public WiFiScanner(Context context, ObservationCallback callback) {
        this.callback = callback;
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    public void startScanning() {
        if (wifiManager == null) {
            Log.e(TAG, "Wi-Fi is not supported on this device.");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi is disabled — cannot read scan results.");
            return;
        }

        isScanning = true;
        Log.d(TAG, "Started Wi-Fi scanning (cache-poll every " + POLL_INTERVAL_MS / 1000 + "s)");
        // Post through the Handler so the first poll also runs on the background executor
        handler.post(this::pollResults);
    }

    public void stopScanning() {
        isScanning = false;
        handler.removeCallbacksAndMessages(null);
        scanExecutor.shutdownNow();
        Log.d(TAG, "Stopped Wi-Fi scanning");
    }

    /** Schedules the next poll on the main-thread Handler, executes the actual read on a background thread. */
    private void pollResults() {
        if (!isScanning) return;
        scanExecutor.execute(() -> {
            readCachedResults();
            // Schedule the next poll back on the Handler (main thread) so the timing
            // is driven by the Handler clock, not the executor queue.
            // Guard again here: stopScanning() may have been called while readCachedResults() ran.
            if (isScanning) {
                handler.postDelayed(this::pollResults, POLL_INTERVAL_MS);
            }
        });
    }

    private void readCachedResults() {
        try {
            // Attempt to trigger a fresh scan. On many devices this is throttled and
            // returns false, but when it works it triggers the OS to refresh the cache.
            try {
                wifiManager.startScan();
            } catch (Exception ignored) {
                // Throttled or missing permission — rely on cache polling instead.
            }

            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null || results.isEmpty()) {
                Log.d(TAG, "Wi-Fi cache is empty — no networks in OS cache yet.");
                return;
            }

            Log.d(TAG, "Wi-Fi cache: " + results.size() + " network(s) found.");
            for (ScanResult result : results) {
                if (result.BSSID == null || result.BSSID.isEmpty()) continue;

                Observation obs = new Observation(
                        "WIFI", result.BSSID, result.level, getCurrentTimestamp());
                obs.setBssid(result.BSSID);
                obs.setSsid(result.SSID);
                obs.setFrequency(result.frequency);

                if (callback != null) {
                    callback.onObservationDetected(obs);
                }

                Log.d(TAG, "Wi-Fi detected: BSSID=" + result.BSSID
                        + "  SSID=" + result.SSID
                        + "  RSSI=" + result.level + " dBm");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission — required to read Wi-Fi results.", e);
        }
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }
}
