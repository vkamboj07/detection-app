package com.footfallanalytics.sdk.model;

import java.util.Map;

public class FootfallMetrics {
    private int totalVisitorsToday;
    private int currentNearbyDevices;
    private int returningVisitors;
    private long averageDwellTimeMs;
    private String peakHour;
    private Map<Integer, Integer> hourlyTrafficTrend;
    private Map<Integer, Integer> last5MinutesTrend;
    private Map<String, Integer> deviceSourceDistribution;
    private Map<String, Integer> categoryDistribution;
    private String peakActivityMinsAgo;

    public int getTotalVisitorsToday() { return totalVisitorsToday; }
    public void setTotalVisitorsToday(int totalVisitorsToday) { this.totalVisitorsToday = totalVisitorsToday; }

    public int getCurrentNearbyDevices() { return currentNearbyDevices; }
    public void setCurrentNearbyDevices(int currentNearbyDevices) { this.currentNearbyDevices = currentNearbyDevices; }

    public int getReturningVisitors() { return returningVisitors; }
    public void setReturningVisitors(int returningVisitors) { this.returningVisitors = returningVisitors; }

    public long getAverageDwellTimeMs() { return averageDwellTimeMs; }
    public void setAverageDwellTimeMs(long averageDwellTimeMs) { this.averageDwellTimeMs = averageDwellTimeMs; }

    public String getPeakHour() { return peakHour; }
    public void setPeakHour(String peakHour) { this.peakHour = peakHour; }

    public Map<Integer, Integer> getHourlyTrafficTrend() { return hourlyTrafficTrend; }
    public void setHourlyTrafficTrend(Map<Integer, Integer> hourlyTrafficTrend) { this.hourlyTrafficTrend = hourlyTrafficTrend; }

    public Map<Integer, Integer> getLast5MinutesTrend() { return last5MinutesTrend; }
    public void setLast5MinutesTrend(Map<Integer, Integer> last5MinutesTrend) { this.last5MinutesTrend = last5MinutesTrend; }

    public Map<String, Integer> getDeviceSourceDistribution() { return deviceSourceDistribution; }
    public void setDeviceSourceDistribution(Map<String, Integer> deviceSourceDistribution) { this.deviceSourceDistribution = deviceSourceDistribution; }

    public Map<String, Integer> getCategoryDistribution() { return categoryDistribution; }
    public void setCategoryDistribution(Map<String, Integer> categoryDistribution) { this.categoryDistribution = categoryDistribution; }

    public String getPeakActivityMinsAgo() { return peakActivityMinsAgo; }
    public void setPeakActivityMinsAgo(String peakActivityMinsAgo) { this.peakActivityMinsAgo = peakActivityMinsAgo; }
}
