package com.example.billboardanalytics.engine;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.SessionEntity;
import com.example.billboardanalytics.util.DeviceCategory;

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

    // SimpleDateFormat is NOT thread-safe — use ThreadLocal so each thread gets its own instance
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public AnalyticsEngine(AnalyticsDao dao) {
        this.dao = dao;
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
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDayMs = cal.getTimeInMillis();
        
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfDayMs = cal.getTimeInMillis();

        String startOfDayStr = DATE_FORMAT.get().format(new Date(startOfDayMs));
        String endOfDayStr = DATE_FORMAT.get().format(new Date(endOfDayMs));
        String fiveMinsAgoStr = DATE_FORMAT.get().format(new Date(fiveMinsAgo));

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

        // 5. Source & Category Distribution
        Map<String, Integer> sourceDistribution = new HashMap<>();
        Map<String, Integer> categoryDistribution = new HashMap<>();
        
        for (Long deviceId : uniqueVisitorsToday) {
            DeviceEntity device = deviceMap.get(deviceId);
            if (device != null) {
                String source = device.source != null ? device.source : "UNKNOWN";

                // Map Protocols
                if (source.equals("WIFI")) {
                    sourceDistribution.merge("Wi-Fi", 1, Integer::sum);
                } else if (source.equals("BLE") || source.equals("BT_CLASSIC")) {
                    sourceDistribution.merge("Bluetooth", 1, Integer::sum);
                } else {
                    sourceDistribution.merge("Unknown", 1, Integer::sum);
                }

                // Category via shared utility
                String category = DeviceCategory.resolve(source, device.deviceIdentifier);
                categoryDistribution.merge(category, 1, Integer::sum);
            }
        }

        // Peak activity: compute how many minutes ago the centre of the peak hour was.
        // e.g. if peakHour=14 the centre is 14:30. If it's currently 15:10, diff = 40 mins.
        // If the peak hour hasn't happened yet today (future hour), report 0.
        Calendar peakCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        peakCal.setTimeInMillis(now);
        peakCal.set(Calendar.HOUR_OF_DAY, peakHour);
        peakCal.set(Calendar.MINUTE, 30); // Approximate centre of the peak hour
        peakCal.set(Calendar.SECOND, 0);
        peakCal.set(Calendar.MILLISECOND, 0);
        long peakDiffMins = (now - peakCal.getTimeInMillis()) / 60000;
        if (peakDiffMins < 0) peakDiffMins = 0; // peak hour is in the future — show 0

        // 6. Calculate Last 5 Minutes Trend
        Map<Integer, Integer> last5MinsTrend = new HashMap<>();
        for (int i = 0; i < 5; i++) last5MinsTrend.put(i, 0);

        long minuteMs = 60 * 1000;
        // i = 0 (now - 5m to now - 4m)
        // i = 4 (now - 1m to now)
        for (int i = 0; i < 5; i++) {
            long bucketStart = now - ((5 - i) * minuteMs);
            long bucketEnd = bucketStart + minuteMs;
            
            Set<Long> uniqueInBucket = new HashSet<>();
            for (SessionEntity session : todaySessions) {
                long sStart = parseTimestamp(session.startTime);
                long sEnd = parseTimestamp(session.endTime);
                
                // If session overlaps with the bucket
                if (sStart <= bucketEnd && sEnd >= bucketStart) {
                    uniqueInBucket.add(session.deviceId);
                }
            }
            last5MinsTrend.put(i, uniqueInBucket.size());
        }

        // 7. Populate Metrics
        metrics.totalVisitorsToday = uniqueVisitorsToday.size();
        metrics.currentNearbyDevices = dao.getNearbyDevicesCount(fiveMinsAgoStr);
        metrics.returningVisitors = returningVisitorsToday.size();
        metrics.averageDwellTimeMs = todaySessions.isEmpty() ? 0 : totalDwellTime / todaySessions.size();
        metrics.peakHour = String.format(Locale.US, "%02d:00 - %02d:00", peakHour, (peakHour + 1) % 24);
        metrics.peakActivityMinsAgo = peakDiffMins + " mins ago";
        metrics.hourlyTrafficTrend = hourlyTrend;
        metrics.last5MinutesTrend = last5MinsTrend;
        metrics.deviceSourceDistribution = sourceDistribution;
        metrics.categoryDistribution = categoryDistribution;

        return metrics;
    }

    private int getHourFromTimestamp(String timestamp) {
        try {
            Date date = DATE_FORMAT.get().parse(timestamp);
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
        if (timestamp == null) return 0;
        try {
            Date date = DATE_FORMAT.get().parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }
}
