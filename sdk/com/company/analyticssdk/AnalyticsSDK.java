package com.company.analyticssdk;

import android.content.Context;
import android.util.Log;

/**
 * Entry point for the Analytics SDK.
 *
 * Usage (typically in Application.onCreate):
 * <pre>
 *     AnalyticsSDK.getInstance().initialize(context, "https://api.example.com/");
 * </pre>
 *
 * Thread safety:
 *  - {@link #getInstance()} uses double-checked locking for safe lazy initialization.
 *  - {@link #initialize(Context, String)} is synchronized so concurrent calls from
 *    multiple threads (e.g. app start races) are safe.
 */
public class AnalyticsSDK {

    private static final String TAG = "AnalyticsSDK";

    // Volatile ensures the instance reference is visible across threads immediately
    private static volatile AnalyticsSDK instance;

    private StorageManager storageManager;
    private DeviceManager  deviceManager;
    private NetworkManager networkManager;

    // Guards against initialize() being called more than once per process
    private boolean initialized = false;

    /** Private constructor enforces the singleton pattern. */
    private AnalyticsSDK() {}

    /**
     * Returns the singleton instance, creating it if necessary.
     * Safe to call from any thread.
     */
    public static AnalyticsSDK getInstance() {
        if (instance == null) {
            synchronized (AnalyticsSDK.class) {
                if (instance == null) {
                    instance = new AnalyticsSDK();
                }
            }
        }
        return instance;
    }

    /**
     * Initializes the SDK and registers the device with the backend if not already done.
     *
     * Calling this more than once per process is a no-op after the first successful call.
     * Safe to call from the main thread — the network request is fully asynchronous.
     *
     * @param context    any Context; application context is used internally to avoid leaks
     * @param apiBaseUrl base URL of the analytics backend, e.g. "https://api.example.com/"
     * @throws IllegalArgumentException if context or apiBaseUrl is null/empty
     */
    public synchronized void initialize(Context context, String apiBaseUrl) {
        if (context == null) {
            throw new IllegalArgumentException("AnalyticsSDK: context must not be null");
        }
        if (apiBaseUrl == null || apiBaseUrl.isEmpty()) {
            throw new IllegalArgumentException("AnalyticsSDK: apiBaseUrl must not be null or empty");
        }

        if (initialized) {
            Log.d(TAG, "Already initialized — skipping");
            return;
        }

        // Always use application context to avoid leaking Activity/Service
        Context appContext = context.getApplicationContext();

        storageManager = new StorageManager(appContext);
        deviceManager  = new DeviceManager(storageManager);
        networkManager = new NetworkManager(apiBaseUrl);

        initialized = true;

        registerDeviceIfNeeded(appContext);
    }

    /**
     * Sends the device ID to the backend only on the first successful launch.
     * Subsequent launches skip the network call entirely.
     */
    private void registerDeviceIfNeeded(Context context) {
        if (storageManager.isRegistered()) {
            Log.d(TAG, "Device already registered");
            return;
        }

        String deviceId = deviceManager.getOrCreateDeviceId(context);

        networkManager.registerDevice(deviceId, new NetworkManager.RegistrationCallback() {
            @Override
            public void onSuccess() {
                // Only advance the flag after a confirmed HTTP 2xx so we retry on the
                // next launch if the network was unavailable this time.
                storageManager.setRegistered(true);
                Log.d(TAG, "Registration successful");
            }

            @Override
            public void onFailure(Exception e) {
                // Leave is_registered=false so the next app launch retries automatically
                Log.e(TAG, "Registration failed: " + e.getMessage());
            }
        });
    }
}
