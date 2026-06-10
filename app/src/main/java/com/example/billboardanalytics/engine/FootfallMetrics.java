package com.example.billboardanalytics.engine;

import java.util.Map;

public class FootfallMetrics {
    public int totalVisitorsToday;
    public int currentNearbyDevices;
    public int returningVisitors;
    public long averageDwellTimeMs;
    public String peakHour; // e.g. "14:00 - 15:00"
    
    // Hourly Traffic Trend: Map of Hour (0-23) -> Number of Sessions
    public Map<Integer, Integer> hourlyTrafficTrend;
    
    // Device Source Distribution: Map of Source ("BLE", "WIFI", "BT_CLASSIC") -> Count
    public Map<String, Integer> deviceSourceDistribution;
}
