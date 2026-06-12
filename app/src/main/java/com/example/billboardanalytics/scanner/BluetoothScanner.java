package com.example.billboardanalytics.scanner;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import com.example.billboardanalytics.data.Observation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private final ObservationCallback callback;

    // SimpleDateFormat is NOT thread-safe. BLE callbacks can arrive on different
    // threads (onScanResult vs onBatchScanResults), so use ThreadLocal.
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public interface ObservationCallback {
        void onObservationDetected(Observation observation);
    }

    public BluetoothScanner(Context context, ObservationCallback callback) {
        this.context = context;
        this.callback = callback;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    @SuppressLint("MissingPermission") // Permissions are handled in Manifest/Activity
    public void startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not supported");
            return;
        }

        startLeScan();
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(leScanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping BLE scan: " + e.getMessage());
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startLeScan() {
        // Re-fetch the scanner in case BT was off when this object was constructed
        if (bluetoothLeScanner == null && bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        if (bluetoothLeScanner != null) {
            android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bluetoothLeScanner.startScan(null, settings, leScanCallback);
            Log.d(TAG, "Started BLE Scanning with SCAN_MODE_LOW_LATENCY");
        } else {
            Log.e(TAG, "BluetoothLeScanner unavailable — BT may still be off");
        }
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }

    // Classic Bluetooth Receiver
    // BLE Scan Callback
    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            ScanRecord record = result.getScanRecord();

            if (device != null) {
                Observation obs = new Observation("BLE", device.getAddress(), rssi, getCurrentTimestamp());
                
                if (record != null) {
                    byte[] rawBytes = record.getBytes();
                    if (rawBytes != null) {
                        obs.setAdvertisementData(bytesToHex(rawBytes));
                    }
                    
                    // Convert Manufacturer Data to Hex String
                    if (record.getManufacturerSpecificData() != null && record.getManufacturerSpecificData().size() > 0) {
                        int key = record.getManufacturerSpecificData().keyAt(0);
                        byte[] mfBytes = record.getManufacturerSpecificData().get(key);
                        if (mfBytes != null) {
                            obs.setManufacturerData(String.format("%04X", key) + bytesToHex(mfBytes));
                        }
                    }
                }
                
                if (callback != null) {
                    callback.onObservationDetected(obs);
                }
            }
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "onBatchScanResults: " + results.size() + " BLE results received");
            for (ScanResult result : results) {
                onScanResult(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "BLE Scan Failed with code: " + errorCode);
        }
    };

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
