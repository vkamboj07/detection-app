package com.footfallanalytics.sdk.scanner;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.footfallanalytics.sdk.model.Observation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothScanner {
    private static final String TAG = "FSDK_BluetoothScanner";
    private static final long DEDUP_WINDOW_MS = 2000;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private final ObservationCallback callback;
    private final boolean enableClassicScanning;

    private volatile boolean isScanning = false;
    private final Map<String, Long> recentlyProcessed = new ConcurrentHashMap<>();

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    public interface ObservationCallback {
        void onObservationDetected(Observation observation);
    }

    public BluetoothScanner(Context context, ObservationCallback callback, boolean enableClassicScanning) {
        this.context = context;
        this.callback = callback;
        this.enableClassicScanning = enableClassicScanning;
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    private final BroadcastReceiver classicBtReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (device != null && !isDuplicate(device.getAddress())) {
                    try {
                        int type = device.getType();
                        if (type == BluetoothDevice.DEVICE_TYPE_LE) return;
                    } catch (SecurityException ignored) {}

                    Observation obs = new Observation("BT_CLASSIC", device.getAddress(), rssi, getCurrentTimestamp());
                    if (callback != null) callback.onObservationDetected(obs);
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not supported");
            return;
        }
        if (isScanning) return;
        isScanning = true;

        if (enableClassicScanning) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(classicBtReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(classicBtReceiver, filter);
            }
        }

        startLeScan();
        if (enableClassicScanning) startClassicDiscovery();
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (!isScanning) return;
        isScanning = false;

        if (bluetoothLeScanner != null) {
            try { bluetoothLeScanner.stopScan(leScanCallback); } catch (Exception ignored) {}
        }
        if (enableClassicScanning) {
            try { bluetoothAdapter.cancelDiscovery(); } catch (Exception ignored) {}
            try { context.unregisterReceiver(classicBtReceiver); } catch (Exception ignored) {}
        }
    }

    @SuppressLint("MissingPermission")
    private void startLeScan() {
        if (bluetoothLeScanner == null && bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        if (bluetoothLeScanner != null) {
            android.bluetooth.le.ScanSettings settings = new android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bluetoothLeScanner.startScan(null, settings, leScanCallback);
        }
    }

    @SuppressLint("MissingPermission")
    private void startClassicDiscovery() {
        if (bluetoothAdapter != null) {
            try { bluetoothAdapter.cancelDiscovery(); } catch (Exception ignored) {}
            if (!bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.startDiscovery();
            }
        }
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }

    private boolean isDuplicate(String mac) {
        long now = System.currentTimeMillis();
        Long prev = recentlyProcessed.put(mac, now);
        boolean dup = prev != null && (now - prev) < DEDUP_WINDOW_MS;
        if (recentlyProcessed.size() > 500) {
            Iterator<Map.Entry<String, Long>> it = recentlyProcessed.entrySet().iterator();
            while (it.hasNext()) {
                if ((now - it.next().getValue()) > DEDUP_WINDOW_MS) {
                    it.remove();
                }
            }
        }
        return dup;
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            ScanRecord record = result.getScanRecord();

            if (device != null && !isDuplicate(device.getAddress())) {
                Observation obs = new Observation("BLE", device.getAddress(), rssi, getCurrentTimestamp());
                if (record != null) {
                    obs.setDeviceName(record.getDeviceName());
                    if (record.getManufacturerSpecificData() != null && record.getManufacturerSpecificData().size() > 0) {
                        int key = record.getManufacturerSpecificData().keyAt(0);
                        byte[] mfBytes = record.getManufacturerSpecificData().get(key);
                        if (mfBytes != null) {
                            obs.setManufacturerData(String.format(Locale.US, "%04X", key) + bytesToHex(mfBytes));
                        }
                    }
                }
                if (callback != null) callback.onObservationDetected(obs);
            }
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            for (ScanResult result : results) {
                onScanResult(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE Scan Failed with code: " + errorCode);
        }
    };

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
