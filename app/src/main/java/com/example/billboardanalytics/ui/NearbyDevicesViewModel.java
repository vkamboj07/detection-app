package com.example.billboardanalytics.ui;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public class NearbyDevicesViewModel extends AndroidViewModel {
    private static final String TAG = "NearbyDevicesViewModel";
    private final AnalyticsDao dao;
    private final MutableLiveData<List<NearbyDevice>> nearbyDevicesLiveData = new MutableLiveData<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    // SimpleDateFormat is NOT thread-safe — use ThreadLocal so the scheduled thread gets its own
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public NearbyDevicesViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getDatabase(application).analyticsDao();
        startPolling();
    }

    public LiveData<List<NearbyDevice>> getNearbyDevices() {
        return nearbyDevicesLiveData;
    }

    private void startPolling() {
        executorService.scheduleWithFixedDelay(() -> {
            try {
                updateDevicesList();
            } catch (Exception e) {
                Log.e(TAG, "Error updating devices list", e);
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void updateDevicesList() {
        long now = System.currentTimeMillis();
        long thresholdMs = now - 300_000; // 5 minutes
        String thresholdStr = DATE_FORMAT.get().format(new Date(thresholdMs));

        // Single query: all devices + their latest observation — no N+1
        List<DeviceEntity> allDevices = dao.getAllDevices();
        List<ObservationEntity> latestObs = dao.getLatestObservationPerDeviceSince(thresholdStr);

        // Build a map: deviceId → latest observation for O(1) lookup
        Map<Long, ObservationEntity> obsMap = new HashMap<>();
        for (ObservationEntity obs : latestObs) {
            obsMap.put(obs.deviceId, obs);
        }

        List<NearbyDevice> uiList = new ArrayList<>();

        for (DeviceEntity device : allDevices) {
            long lastSeenMs = parseTimestamp(device.lastSeen);
            if (lastSeenMs < thresholdMs) continue;

            ObservationEntity obs = obsMap.get(device.id);
            if (obs == null) continue; // no observations yet

            NearbyDevice nd = new NearbyDevice();
            nd.databaseId    = device.id;
            nd.deviceId      = device.deviceIdentifier != null ? device.deviceIdentifier : "Unknown";
            nd.source        = device.source != null ? device.source : "UNKNOWN";
            nd.rssi          = obs.rssi;
            nd.distanceMeters = calculateDistance(obs.rssi);
            nd.lastSeenMs    = lastSeenMs;
            nd.lastSeenText  = formatTimeAgo(now - lastSeenMs);
            nd.status        = calculateStatus(now - lastSeenMs);

            uiList.add(nd);
        }

        uiList.sort((a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs));
        nearbyDevicesLiveData.postValue(uiList);
    }

    private double calculateDistance(int rssi) {
        int txPower = -59;
        if (rssi == 0) return -1.0;
        double ratio = (txPower - rssi) / (10.0 * 2.0);
        return Math.pow(10, ratio);
    }

    private String calculateStatus(long elapsedMs) {
        if (elapsedMs < 10_000)  return "ACTIVE";
        if (elapsedMs < 60_000)  return "IDLE";
        return "LEFT";
    }

    private String formatTimeAgo(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        if (seconds < 60) return seconds + " sec ago";
        return (seconds / 60) + " min ago";
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

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdownNow(); // interrupt any in-flight DB queries immediately
    }
}
