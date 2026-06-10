package com.example.billboardanalytics.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.example.billboardanalytics.data.Observation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@SuppressWarnings("deprecation")
public class WiFiScanner {
    private static final String TAG = "WiFiScanner";
    
    private final Context context;
    private final WifiManager wifiManager;
    private final ObservationCallback callback;
    
    private final SimpleDateFormat dateFormat;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long SCAN_INTERVAL_MS = 30_000; // 30 seconds to respect OS throttling

    public interface ObservationCallback {
        void onObservationDetected(Observation observation);
    }

    public WiFiScanner(Context context, ObservationCallback callback) {
        this.context = context;
        this.callback = callback;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void startScanning() {
        if (wifiManager == null) {
            Log.e(TAG, "Wi-Fi is not supported on this device.");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wi-Fi is disabled.");
            // Android 10+ does not allow enabling Wi-Fi programmatically via WifiManager.setWifiEnabled()
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        ContextCompat.registerReceiver(context, wifiScanReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED);
        
        isScanning = true;
        triggerScan();
        Log.d(TAG, "Started Wi-Fi Scanning");
    }

    public void stopScanning() {
        isScanning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            context.unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
        Log.d(TAG, "Stopped Wi-Fi Scanning");
    }

    private void triggerScan() {
        if (isScanning && wifiManager != null) {
            boolean success = wifiManager.startScan();
            if (!success) {
                Log.w(TAG, "Wi-Fi scan failed or throttled by OS");
            }
        }
    }

    private String getCurrentTimestamp() {
        return dateFormat.format(new Date());
    }

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // A scan failure usually means the OS throttled the scan.
            // We still fetch the latest cached results regardless.
            scanSuccess();

            // Re-trigger scan after a delay to avoid OS throttling
            if (isScanning) {
                handler.postDelayed(() -> triggerScan(), SCAN_INTERVAL_MS);
            }
        }
    };

    private void scanSuccess() {
        try {
            List<ScanResult> results = wifiManager.getScanResults();
            for (ScanResult result : results) {
                // Set the BSSID and other specific fields.
                Observation obs = new Observation("WIFI", result.BSSID, result.level, getCurrentTimestamp());
                obs.setBssid(result.BSSID);
                obs.setSsid(result.SSID);
                obs.setFrequency(result.frequency);
                
                if (callback != null) {
                    callback.onObservationDetected(obs);
                }
                
                Log.d(TAG, "Wi-Fi Found: " + obs.toJson());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing location permissions for Wi-Fi scanning", e);
        }
    }
}
