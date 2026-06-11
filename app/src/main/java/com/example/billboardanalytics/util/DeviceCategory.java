package com.example.billboardanalytics.util;

/**
 * Single source of truth for mapping a device source + identifier to a human-readable category.
 * Previously this logic was duplicated in AnalyticsEngine, NearbyDeviceAdapter, and
 * DeviceDetailViewModel.
 */
public final class DeviceCategory {

    private DeviceCategory() {}

    /**
     * Returns a category label for a device.
     *
     * @param source           The scan source: "WIFI", "BLE", or "BT_CLASSIC".
     * @param deviceIdentifier The MAC/BSSID string (may be null).
     * @return A human-readable category string.
     */
    public static String resolve(String source, String deviceIdentifier) {
        if ("WIFI".equals(source)) {
            return "Router/AP";
        }
        // BLE / BT_CLASSIC — deterministic bucket from MAC hash
        int hash = deviceIdentifier != null
                ? Math.abs(deviceIdentifier.hashCode()) % 5
                : 4;
        switch (hash) {
            case 0: return "Phones";
            case 1: return "Audio";
            case 2: return "Laptop";
            case 3: return "Watches";
            default: return "Unknown";
        }
    }
}
