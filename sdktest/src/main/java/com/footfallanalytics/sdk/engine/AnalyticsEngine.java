package com.footfallanalytics.sdk.engine;

import com.footfallanalytics.sdk.data.AnalyticsDao;
import com.footfallanalytics.sdk.data.DeviceEntity;
import com.footfallanalytics.sdk.data.SessionEntity;
import com.footfallanalytics.sdk.model.FootfallMetrics;
import com.footfallanalytics.sdk.util.DeviceCategory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    public AnalyticsEngine(AnalyticsDao dao) {
        this.dao = dao;
    }

    public FootfallMetrics generateMetricsForToday() {
        FootfallMetrics metrics = new FootfallMetrics();
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

        List<SessionEntity> todaySessions = dao.getSessionsForDateRange(startOfDayStr, endOfDayStr);
        List<DeviceEntity> allDevices = dao.getAllDevices();

        Map<Long, DeviceEntity> deviceMap = new HashMap<>();
        for (DeviceEntity d : allDevices) deviceMap.put(d.id, d);

        Set<Long> uniqueVisitorsToday = new HashSet<>();
        Set<Long> returningVisitorsToday = new HashSet<>();
        long totalDwellTime = 0;
        Map<Integer, Integer> hourlyTrend = new HashMap<>();
        for (int i = 0; i < 24; i++) hourlyTrend.put(i, 0);

        for (SessionEntity session : todaySessions) {
            uniqueVisitorsToday.add(session.deviceId);
            totalDwellTime += session.duration;
            int hour = getHourFromTimestamp(session.startTime);
            Integer prevHourly = hourlyTrend.get(hour);
            hourlyTrend.put(hour, (prevHourly == null ? 0 : prevHourly) + 1);

            DeviceEntity device = deviceMap.get(session.deviceId);
            if (device != null && device.firstSeen != null) {
                long firstSeenMs = parseTimestamp(device.firstSeen);
                if (firstSeenMs < startOfDayMs) {
                    returningVisitorsToday.add(device.id);
                }
            }
        }

        int peakHour = 0;
        int maxSessions = 0;
        for (Map.Entry<Integer, Integer> entry : hourlyTrend.entrySet()) {
            if (entry.getValue() > maxSessions) {
                maxSessions = entry.getValue();
                peakHour = entry.getKey();
            }
        }

        Map<String, Integer> sourceDistribution = new HashMap<>();
        Map<String, Integer> categoryDistribution = new HashMap<>();

        for (Long deviceId : uniqueVisitorsToday) {
            DeviceEntity device = deviceMap.get(deviceId);
            if (device != null) {
                String source = device.source != null ? device.source : "UNKNOWN";
                String sourceKey = "Unknown";
                if ("WIFI".equals(source)) {
                    sourceKey = "Wi-Fi";
                } else if ("BLE".equals(source) || "BT_CLASSIC".equals(source)) {
                    sourceKey = "Bluetooth";
                }
                Integer prevSrc = sourceDistribution.get(sourceKey);
                sourceDistribution.put(sourceKey, (prevSrc == null ? 0 : prevSrc) + 1);

                String category = DeviceCategory.resolve(source, device.deviceIdentifier);
                Integer prevCat = categoryDistribution.get(category);
                categoryDistribution.put(category, (prevCat == null ? 0 : prevCat) + 1);
            }
        }

        String peakActivityText;
        if (todaySessions.isEmpty()) {
            peakActivityText = "N/A";
        } else {
            Calendar peakCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            peakCal.setTimeInMillis(now);
            peakCal.set(Calendar.HOUR_OF_DAY, peakHour);
            peakCal.set(Calendar.MINUTE, 30);
            peakCal.set(Calendar.SECOND, 0);
            peakCal.set(Calendar.MILLISECOND, 0);
            long peakDiffMins = (now - peakCal.getTimeInMillis()) / 60000;
            if (peakDiffMins < 0) peakDiffMins = 0;
            peakActivityText = peakDiffMins + " mins ago";
        }

        List<SessionEntity> recentSessions = new ArrayList<>();
        for (SessionEntity s : todaySessions) {
            if (parseTimestamp(s.endTime) >= fiveMinsAgo) recentSessions.add(s);
        }

        Map<Integer, Integer> last5MinsTrend = new HashMap<>();
        for (int i = 0; i < 5; i++) last5MinsTrend.put(i, 0);

        long minuteMs = 60 * 1000;
        for (int i = 0; i < 5; i++) {
            long bucketStart = now - ((5 - i) * minuteMs);
            long bucketEnd = bucketStart + minuteMs;
            Set<Long> uniqueInBucket = new HashSet<>();
            for (SessionEntity session : recentSessions) {
                long sStart = parseTimestamp(session.startTime);
                long sEnd = parseTimestamp(session.endTime);
                if (sStart <= bucketEnd && sEnd >= bucketStart) {
                    uniqueInBucket.add(session.deviceId);
                }
            }
            last5MinsTrend.put(i, uniqueInBucket.size());
        }

        metrics.setTotalVisitorsToday(uniqueVisitorsToday.size());
        metrics.setCurrentNearbyDevices(dao.getNearbyDevicesCount(fiveMinsAgoStr));
        metrics.setReturningVisitors(returningVisitorsToday.size());
        metrics.setAverageDwellTimeMs(todaySessions.isEmpty() ? 0 : totalDwellTime / todaySessions.size());
        metrics.setPeakHour(todaySessions.isEmpty() ? "N/A" :
                String.format(Locale.US, "%02d:00 - %02d:00", peakHour, (peakHour + 1) % 24));
        metrics.setPeakActivityMinsAgo(peakActivityText);
        metrics.setHourlyTrafficTrend(hourlyTrend);
        metrics.setLast5MinutesTrend(last5MinsTrend);
        metrics.setDeviceSourceDistribution(sourceDistribution);
        metrics.setCategoryDistribution(categoryDistribution);

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
        } catch (ParseException ignored) {}
        return 0;
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null) return 0;
        try {
            Date date = DATE_FORMAT.get().parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) { return 0; }
    }
}
