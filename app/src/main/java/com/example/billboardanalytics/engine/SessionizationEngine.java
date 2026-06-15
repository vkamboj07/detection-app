package com.example.billboardanalytics.engine;

import android.util.Log;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;
import com.example.billboardanalytics.data.SessionEntity;
import com.example.billboardanalytics.sync.SupabaseSyncManager;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SessionizationEngine {
    private static final String TAG = "SessionizationEngine";
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes
    // Maximum number of pending detections queued before new ones are dropped.
    // BLE can fire 10-20 callbacks/second; a cap of 200 gives ~10-20s of buffer
    // without letting the queue grow without bound during extended scanning bursts.
    private static final int MAX_QUEUE_DEPTH = 200;

    private final AnalyticsDao dao;
    private final SupabaseSyncManager syncManager;
    // Bounded queue with CallerRunsPolicy would block the scanner thread — use
    // DiscardPolicy instead so scanner callbacks are never stalled.
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_DEPTH),
            new ThreadPoolExecutor.DiscardPolicy());

    // SimpleDateFormat is NOT thread-safe — use ThreadLocal to give each thread its own instance
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public SessionizationEngine(AnalyticsDao dao, SupabaseSyncManager syncManager) {
        this.dao = dao;
        this.syncManager = syncManager;
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
                    // duration updated — automatic sync will pick it up
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

            // 5. Push new data to Supabase so the web dashboard receives it
            syncManager.syncAsync();
        });
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }

    private long parseTimestamp(String timestamp) {
        try {
            Date date = DATE_FORMAT.get().parse(timestamp);
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

    /** Call from the service's onDestroy to release threads and flush any pending sync. */
    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        syncManager.shutdown();
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
