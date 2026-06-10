package com.example.billboardanalytics.engine;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.SessionEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class AnalyticsEngine {
    private final AnalyticsDao dao;
    private final SimpleDateFormat dateFormat;

    public AnalyticsEngine(AnalyticsDao dao) {
        this.dao = dao;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public FootfallMetrics generateMetricsForToday() {
        FootfallMetrics metrics = new FootfallMetrics();

        // 1. Setup Time Ranges
        long now = System.currentTimeMillis();
        long fiveMinsAgo = now - (5 * 60 * 1000);
        
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startOfDayMs = cal.getTimeInMillis();
        
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfDayMs = cal.getTimeInMillis();

        String startOfDayStr = dateFormat.format(new Date(startOfDayMs));
        String endOfDayStr = dateFormat.format(new Date(endOfDayMs));
        String fiveMinsAgoStr = dateFormat.format(new Date(fiveMinsAgo));

        // 2. Fetch Data
        List<SessionEntity> todaySessions = dao.getSessionsForDateRange(startOfDayStr, endOfDayStr);
        List<DeviceEntity> allDevices = dao.getAllDevices();
        
        // Map devices for quick lookup
        Map<Long, DeviceEntity> deviceMap = new HashMap<>();
        for (DeviceEntity d : allDevices) {
            deviceMap.put(d.id, d);
        }

        // 3. Process Sessions
        Set<Long> uniqueVisitorsToday = new HashSet<>();
        Set<Long> returningVisitorsToday = new HashSet<>();
        long totalDwellTime = 0;
        
        Map<Integer, Integer> hourlyTrend = new HashMap<>();
        for (int i = 0; i < 24; i++) hourlyTrend.put(i, 0);

        for (SessionEntity session : todaySessions) {
            uniqueVisitorsToday.add(session.deviceId);
            totalDwellTime += session.duration;

            // Hourly trend
            int hour = getHourFromTimestamp(session.startTime);
            hourlyTrend.merge(hour, 1, Integer::sum);

            // Check if returning
            DeviceEntity device = deviceMap.get(session.deviceId);
            if (device != null) {
                long firstSeenMs = parseTimestamp(device.firstSeen);
                if (firstSeenMs < startOfDayMs) {
                    returningVisitorsToday.add(device.id);
                }
            }
        }

        // 4. Calculate Peak Hour
        int peakHour = 0;
        int maxSessions = 0;
        for (Map.Entry<Integer, Integer> entry : hourlyTrend.entrySet()) {
            if (entry.getValue() > maxSessions) {
                maxSessions = entry.getValue();
                peakHour = entry.getKey();
            }
        }

        // 5. Source Distribution
        Map<String, Integer> sourceDistribution = new HashMap<>();
        for (Long deviceId : uniqueVisitorsToday) {
            DeviceEntity device = deviceMap.get(deviceId);
            if (device != null) {
                String source = device.source != null ? device.source : "UNKNOWN";
                sourceDistribution.merge(source, 1, Integer::sum);
            }
        }

        // 6. Populate Metrics
        metrics.totalVisitorsToday = uniqueVisitorsToday.size();
        metrics.currentNearbyDevices = dao.getNearbyDevicesCount(fiveMinsAgoStr);
        metrics.returningVisitors = returningVisitorsToday.size();
        metrics.averageDwellTimeMs = todaySessions.isEmpty() ? 0 : totalDwellTime / todaySessions.size();
        metrics.peakHour = String.format(Locale.US, "%02d:00 - %02d:00", peakHour, (peakHour + 1) % 24);
        metrics.hourlyTrafficTrend = hourlyTrend;
        metrics.deviceSourceDistribution = sourceDistribution;

        return metrics;
    }

    private int getHourFromTimestamp(String timestamp) {
        try {
            Date date = dateFormat.parse(timestamp);
            if (date != null) {
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(date);
                return cal.get(Calendar.HOUR_OF_DAY);
            }
        } catch (ParseException e) {
            // fallback
        }
        return 0;
    }

    private long parseTimestamp(String timestamp) {
        try {
            Date date = dateFormat.parse(timestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (ParseException e) {
            return System.currentTimeMillis();
        }
    }
}
