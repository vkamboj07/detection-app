package com.example.billboardanalytics.engine;

import android.util.Log;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;
import com.example.billboardanalytics.data.SessionEntity;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionizationEngine {
    private static final String TAG = "SessionizationEngine";
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    private final AnalyticsDao dao;
    private final SimpleDateFormat dateFormat;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SessionizationEngine(AnalyticsDao dao) {
        this.dao = dao;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void processDetection(String deviceIdentifier, String source, int rssi) {
        executor.execute(() -> {
            String timestamp = getCurrentTimestamp();

            // 1. Get or Create Device
            DeviceEntity device = dao.getDeviceByIdentifier(deviceIdentifier);
            if (device == null) {
                DeviceEntity newDevice = new DeviceEntity();
                newDevice.deviceIdentifier = deviceIdentifier;
                newDevice.source = source;
                newDevice.firstSeen = timestamp;
                newDevice.lastSeen = timestamp;
                long insertedId = dao.insertDevice(newDevice);
                if (insertedId == -1) {
                    // Another thread inserted this device first — fetch the existing record
                    device = dao.getDeviceByIdentifier(deviceIdentifier);
                    if (device == null) {
                        Log.e(TAG, "Failed to insert or retrieve device: " + deviceIdentifier);
                        return;
                    }
                    device.lastSeen = timestamp;
                    dao.updateDevice(device);
                } else {
                    newDevice.id = insertedId;
                    device = newDevice;
                }
            } else {
                device.lastSeen = timestamp;
                dao.updateDevice(device);
            }

            // 2. Insert Observation
            ObservationEntity observation = new ObservationEntity();
            observation.deviceId = device.id;
            observation.timestamp = timestamp;
            observation.rssi = rssi;
            observation.source = source;
            dao.insertObservation(observation);

            // 3. Process Session
            SessionEntity latestSession = dao.getLatestSessionForDevice(device.id);

            boolean newSession = false;

            if (latestSession == null) {
                newSession = true;
            } else {
                long lastSeenTime = parseTimestamp(latestSession.endTime);
                long currentTime = parseTimestamp(timestamp);

                if (currentTime - lastSeenTime > SESSION_TIMEOUT_MS) {
                    newSession = true; // Absent for > 10 minutes
                } else {
                    // Update existing session
                    latestSession.endTime = timestamp;
                    latestSession.duration = currentTime - parseTimestamp(latestSession.startTime);
                    dao.updateSession(latestSession);
                }
            }

            if (newSession) {
                latestSession = new SessionEntity();
                latestSession.deviceId = device.id;
                latestSession.startTime = timestamp;
                latestSession.endTime = timestamp;
                latestSession.duration = 0;
                latestSession.id = dao.insertSession(latestSession);
            }

            // 4. Output the session metrics
            int detections = dao.getObservationCountForSession(device.id, latestSession.startTime, latestSession.endTime);
            
            SessionOutput output = new SessionOutput();
            output.visitor = "visitor_" + device.id;
            output.sessionDuration = formatDuration(latestSession.duration);
            output.detections = detections;

            Log.d(TAG, "Session Output: " + new Gson().toJson(output));
        });
    }

    private String getCurrentTimestamp() {
        return dateFormat.format(new Date());
    }

    private long parseTimestamp(String timestamp) {
        try {
            Date date = dateFormat.parse(timestamp);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing timestamp: " + timestamp, e);
            return System.currentTimeMillis();
        }
    }

    private String formatDuration(long durationMs) {
        long minutes = (durationMs / 1000) / 60;
        return minutes + "m";
    }

    // DTO for JSON Output
    private static class SessionOutput {
        @SerializedName("visitor")
        String visitor;

        @SerializedName("session_duration")
        String sessionDuration;

        @SerializedName("detections")
        int detections;
    }
}
