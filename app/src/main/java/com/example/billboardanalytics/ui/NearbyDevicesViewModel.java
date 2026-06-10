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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NearbyDevicesViewModel extends AndroidViewModel {
    private static final String TAG = "NearbyDevicesViewModel";
    private final AnalyticsDao dao;
    private final MutableLiveData<List<NearbyDevice>> nearbyDevicesLiveData = new MutableLiveData<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final SimpleDateFormat dateFormat;

    public NearbyDevicesViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getDatabase(application).analyticsDao();
        
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        startPolling();
    }

    public LiveData<List<NearbyDevice>> getNearbyDevices() {
        return nearbyDevicesLiveData;
    }

    private void startPolling() {
        // Poll database every 2 seconds for fresh UI
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
        // Fetch devices seen in the last 5 minutes (300,000 ms) to keep the list clean
        long thresholdMs = now - 300000;

        // For real-time, we could just get all devices and their latest observation.
        // Since we don't have a complex JOIN query set up, we'll fetch all devices and 
        // filter them in memory (fine for this scale).
        List<DeviceEntity> allDevices = dao.getAllDevices();
        List<NearbyDevice> uiList = new ArrayList<>();

        for (DeviceEntity device : allDevices) {
            long lastSeenMs = parseTimestamp(device.lastSeen);
            if (lastSeenMs >= thresholdMs) {
                // Fetch latest observation for RSSI (in a real app, optimize this with a JOIN)
                List<ObservationEntity> obsList = dao.getObservationsForDevice(device.id);
                if (!obsList.isEmpty()) {
                    ObservationEntity latestObs = obsList.get(0);
                    
                    NearbyDevice nd = new NearbyDevice();
                    nd.databaseId = device.id;
                    nd.deviceId = device.deviceIdentifier != null ? device.deviceIdentifier : "Unknown";
                    nd.source = device.source;
                    nd.rssi = latestObs.rssi;
                    nd.distanceMeters = calculateDistance(latestObs.rssi);
                    nd.lastSeenMs = lastSeenMs;
                    nd.lastSeenText = formatTimeAgo(now - lastSeenMs);
                    nd.status = calculateStatus(now - lastSeenMs);
                    
                    uiList.add(nd);
                }
            }
        }

        // Sort by last seen (most recent first)
        uiList.sort((a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs));
        
        nearbyDevicesLiveData.postValue(uiList);
    }

    private double calculateDistance(int rssi) {
        // Simple log-distance path loss model
        // Distance = 10 ^ ((Measured Power - RSSI) / (10 * N))
        int txPower = -59; // typical Tx power at 1 meter
        if (rssi == 0) return -1.0;
        double ratio = (txPower - rssi) / (10.0 * 2.0); // N = 2.0 (free space)
        return Math.pow(10, ratio);
    }

    private String calculateStatus(long elapsedMs) {
        if (elapsedMs < 10000) {
            return "ACTIVE";
        } else if (elapsedMs < 60000) {
            return "IDLE";
        } else {
            return "LEFT";
        }
    }

    private String formatTimeAgo(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        if (seconds < 60) {
            return seconds + " sec ago";
        } else {
            long minutes = seconds / 60;
            return minutes + " min ago";
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            Date date = dateFormat.parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
