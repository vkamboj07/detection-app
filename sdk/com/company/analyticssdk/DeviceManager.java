package com.company.analyticssdk;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

/**
 * Responsible for resolving the stable device identifier.
 *
 * Priority:
 *  1. Previously persisted ID in SharedPreferences (covers all subsequent launches)
 *  2. Settings.Secure.ANDROID_ID — requires no dangerous permissions
 *  3. Random UUID — fallback when ANDROID_ID is null or empty (emulators, some ROMs)
 *
 * The resolved ID is always persisted so the same value is returned on every call.
 */
class DeviceManager {

    private static final String TAG = "AnalyticsSDK";

    private final StorageManager storageManager;

    DeviceManager(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    /**
     * Returns the stable device ID, resolving and persisting it if this is the first call.
     *
     * @param context used to read ANDROID_ID; should be application context
     * @return a non-null, non-empty device identifier string
     */
    String getOrCreateDeviceId(Context context) {
        // 1. Return the already-persisted ID if available
        String stored = storageManager.getDeviceId();
        if (stored != null && !stored.isEmpty()) {
            Log.d(TAG, "Device ID: " + stored);
            return stored;
        }

        // 2. Try ANDROID_ID — available without any dangerous permissions
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);

        String deviceId;
        if (androidId != null && !androidId.isEmpty()) {
            deviceId = androidId;
        } else {
            // 3. Fall back to a random UUID and persist it for consistency
            deviceId = UUID.randomUUID().toString();
            Log.d(TAG, "ANDROID_ID unavailable — generated UUID fallback");
        }

        storageManager.saveDeviceId(deviceId);
        Log.d(TAG, "Device ID: " + deviceId);
        return deviceId;
    }
}
