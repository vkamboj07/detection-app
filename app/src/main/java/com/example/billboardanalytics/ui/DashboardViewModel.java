package com.example.billboardanalytics.ui;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.engine.AnalyticsEngine;
import com.example.billboardanalytics.engine.FootfallMetrics;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashboardViewModel extends AndroidViewModel {
    private static final String TAG = "DashboardViewModel";
    private final AnalyticsEngine engine;
    private final MutableLiveData<FootfallMetrics> metricsLiveData = new MutableLiveData<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        AnalyticsDao dao = AppDatabase.getDatabase(application).analyticsDao();
        engine = new AnalyticsEngine(dao);

        startPolling();
    }

    public LiveData<FootfallMetrics> getMetrics() {
        return metricsLiveData;
    }

    private void startPolling() {
        // Poll database every 5 seconds for dashboard updates
        executorService.scheduleWithFixedDelay(() -> {
            try {
                FootfallMetrics metrics = engine.generateMetricsForToday();
                metricsLiveData.postValue(metrics);
            } catch (Exception e) {
                Log.e(TAG, "Error generating metrics", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdownNow();
    }
}
