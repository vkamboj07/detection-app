package com.footfallanalytics.sdk.engine;

import android.util.Log;

import com.footfallanalytics.sdk.data.AnalyticsDao;
import com.footfallanalytics.sdk.data.DeviceEntity;
import com.footfallanalytics.sdk.data.ObservationEntity;
import com.footfallanalytics.sdk.data.SessionEntity;
import com.footfallanalytics.sdk.sync.SupabaseSyncManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SessionizationEngine {
    private static final String TAG = "FSDK_SessionEngine";
    private static final int MAX_QUEUE_DEPTH = 200;

    private final AnalyticsDao dao;
    private final SupabaseSyncManager syncManager;
    private final long sessionTimeoutMs;
    private final EngineCallback callback;

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_DEPTH),
            (r, exec) -> Log.w(TAG, "Detection dropped - queue full"));

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public interface EngineCallback {
        void onSessionCreated(long deviceId, long sessionId, long duration);
        void onDetectionProcessed();
    }

    public SessionizationEngine(AnalyticsDao dao, SupabaseSyncManager syncManager,
                                 long sessionTimeoutMs, EngineCallback callback) {
        this.dao = dao;
        this.syncManager = syncManager;
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.callback = callback;
    }

    public void processDetection(String deviceIdentifier, String source, int rssi) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            String timestamp = getCurrentTimestamp();

            DeviceEntity device = dao.getDeviceByIdentifier(deviceIdentifier);
            if (device == null) {
                DeviceEntity newDevice = new DeviceEntity();
                newDevice.deviceIdentifier = deviceIdentifier;
                newDevice.source = source;
                newDevice.firstSeen = timestamp;
                newDevice.lastSeen = timestamp;
                long insertedId = dao.insertDevice(newDevice);
                if (insertedId == -1) {
                    device = dao.getDeviceByIdentifier(deviceIdentifier);
                    if (device == null) return;
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

            ObservationEntity obs = new ObservationEntity();
            obs.deviceId = device.id;
            obs.timestamp = timestamp;
            obs.rssi = rssi;
            obs.source = source;
            dao.insertObservation(obs);

            SessionEntity latestSession = dao.getLatestSessionForDevice(device.id);
            boolean newSession = false;

            if (latestSession == null) {
                newSession = true;
            } else {
                long lastSeenTime = parseTimestamp(latestSession.endTime);
                long currentTime = parseTimestamp(timestamp);
                if (currentTime - lastSeenTime > sessionTimeoutMs) {
                    newSession = true;
                } else {
                    latestSession.endTime = timestamp;
                    latestSession.duration = currentTime - parseTimestamp(latestSession.startTime);
                    dao.updateSession(latestSession);
                    syncManager.markSessionDirty(latestSession.id);
                }
            }

            if (newSession) {
                latestSession = new SessionEntity();
                latestSession.deviceId = device.id;
                latestSession.startTime = timestamp;
                latestSession.endTime = timestamp;
                latestSession.duration = 0;
                latestSession.id = dao.insertSession(latestSession);
                if (callback != null) {
                    callback.onSessionCreated(device.id, latestSession.id, 0);
                }
            }

            syncManager.syncAsync();
            if (callback != null) callback.onDetectionProcessed();
        });
    }

    public void shutdown() {
        executor.shutdown();
        try { executor.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { executor.shutdownNow(); }
        syncManager.shutdown();
    }

    private String getCurrentTimestamp() {
        return DATE_FORMAT.get().format(new Date());
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null) return 0;
        try {
            Date date = DATE_FORMAT.get().parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) { return 0; }
    }
}
