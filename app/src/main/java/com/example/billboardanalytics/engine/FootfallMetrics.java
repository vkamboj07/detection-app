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
    
    // Minute Traffic Trend: Map of minute offset (0 to 4, 0 being oldest, 4 being newest) -> Number of unique devices active
    public Map<Integer, Integer> last5MinutesTrend;
    
    // Device Source Distribution: Map of Source ("Bluetooth", "Wi-Fi") -> Count
    public Map<String, Integer> deviceSourceDistribution;
    
    // Category Distribution: Map of Category ("Phones", "Router/AP", etc) -> Count
    public Map<String, Integer> categoryDistribution;
    
    // String representing how many mins ago the peak activity occurred
    public String peakActivityMinsAgo;
}
