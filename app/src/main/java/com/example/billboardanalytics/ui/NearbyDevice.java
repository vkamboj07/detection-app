package com.example.billboardanalytics.ui;

public class NearbyDevice {
    public long databaseId; // Used for passing to Detail Screen
    public String deviceId; // e.g. "visitor_1032"
    public String source; // BLE, WIFI, etc.
    public int rssi; // -55
    public double distanceMeters; // 2.3
    public String lastSeenText; // "2 sec ago"
    public String status; // ACTIVE, IDLE, LEFT
    public long lastSeenMs; // For sorting and status updates
}
