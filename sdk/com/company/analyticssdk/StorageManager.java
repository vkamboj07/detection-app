package com.company.analyticssdk;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Handles all persistent local storage for the SDK via SharedPreferences.
 * Thread-safe: SharedPreferences reads/writes are atomic for single key operations.
 */
class StorageManager {

    private static final String PREFS_NAME       = "analytics_sdk_prefs";
    private static final String KEY_DEVICE_ID    = "device_id";
    private static final String KEY_IS_REGISTERED = "is_registered";

    private final SharedPreferences prefs;

    StorageManager(Context context) {
        // Use application context to avoid leaking Activity/Service references
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the persisted device ID, or null if none has been saved yet. */
    String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, null);
    }

    /** Persists the device ID. */
    void saveDeviceId(String deviceId) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }

    /** Returns true if the device has already been successfully registered with the backend. */
    boolean isRegistered() {
        return prefs.getBoolean(KEY_IS_REGISTERED, false);
    }

    /**
     * Marks the device as registered. Only called after a confirmed successful
     * HTTP response so the flag is never set on failure.
     */
    void setRegistered(boolean registered) {
        prefs.edit().putBoolean(KEY_IS_REGISTERED, registered).apply();
    }
}
