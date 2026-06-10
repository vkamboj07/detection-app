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
import com.example.billboardanalytics.data.SessionEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceDetailViewModel extends AndroidViewModel {
    private static final String TAG = "DeviceDetailViewModel";
    private final AnalyticsDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat;

    private final MutableLiveData<DeviceDetailData> detailData = new MutableLiveData<>();

    public DeviceDetailViewModel(@NonNull Application application) {
        super(application);
        dao = AppDatabase.getDatabase(application).analyticsDao();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public LiveData<DeviceDetailData> getDetailData() {
        return detailData;
    }

    public void loadDeviceDetails(long deviceId) {
        executor.execute(() -> {
            DeviceEntity device = dao.getDeviceById(deviceId);
            if (device == null) return;

            List<ObservationEntity> observations = dao.getObservationsForDevice(deviceId);
            List<SessionEntity> sessions = dao.getAllSessionsForDevice(deviceId);

            DeviceDetailData data = new DeviceDetailData();
            data.anonymousId = "visitor_" + device.id;
            data.macAddress = device.deviceIdentifier;
            
            // Compute category
            if (device.source != null && device.source.equals("WIFI")) {
                data.category = "Router/AP";
            } else {
                int hash = device.deviceIdentifier != null ? Math.abs(device.deviceIdentifier.hashCode()) % 5 : 4;
                switch (hash) {
                    case 0: data.category = "Phones"; break;
                    case 1: data.category = "Audio"; break;
                    case 2: data.category = "Laptop"; break;
                    case 3: data.category = "Watches"; break;
                    default: data.category = "Unknown"; break;
                }
            }
            
            // Format dates simply
            data.firstSeen = formatDateString(device.firstSeen);
            data.lastSeen = formatDateString(device.lastSeen);
            
            data.detectionCount = observations.size();
            
            long totalRssi = 0;
            for (ObservationEntity obs : observations) {
                totalRssi += obs.rssi;
            }
            data.averageRssi = observations.isEmpty() ? 0 : (int)(totalRssi / observations.size());
            
            data.sessionCount = sessions.size();
            
            long totalDwellMs = 0;
            for (SessionEntity session : sessions) {
                totalDwellMs += session.duration;
            }
            data.totalDwellTime = formatDuration(totalDwellMs);
            
            data.observations = observations; // To plot the graph

            detailData.postValue(data);
        });
    }

    private String formatDateString(String isoDate) {
        if (isoDate == null) return "Unknown";
        try {
            Date date = dateFormat.parse(isoDate);
            if (date != null) {
                SimpleDateFormat outFormat = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US);
                return outFormat.format(date);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date string: " + isoDate, e);
        }
        return isoDate;
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    public static class DeviceDetailData {
        public String anonymousId;
        public String macAddress;
        public String category;
        public String firstSeen;
        public String lastSeen;
        public int detectionCount;
        public int averageRssi;
        public int sessionCount;
        public String totalDwellTime;
        public List<ObservationEntity> observations;
    }
}
