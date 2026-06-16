package com.example.billboardanalytics.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.billboardanalytics.R;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.engine.FootfallMetrics;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "tracker_prefs";
    private static final String KEY_IS_TRACKING = "is_tracking";

    private TextView tvCurrentDevices, tvTotalVisitors, tvReturningVisitors, tvAvgDwellTime, tvPeakHour;
    private BarChart hourlyTrafficChart;
    private LineChart activeDevicesLineChart;
    private PieChart categoryPieChart;
    private PieChart sourcePieChart;

    private boolean isTracking = false;
    private Button btnStartTracker;
    private ExecutorService trackerExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Restore tracking state from prefs so button reflects reality after process restart
        isTracking = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_IS_TRACKING, false);

        tvCurrentDevices = findViewById(R.id.tvCurrentDevices);
        tvTotalVisitors = findViewById(R.id.tvTotalVisitors);
        tvReturningVisitors = findViewById(R.id.tvReturningVisitors);
        tvAvgDwellTime = findViewById(R.id.tvAvgDwellTime);
        tvPeakHour = findViewById(R.id.tvPeakHour);

        hourlyTrafficChart = findViewById(R.id.hourlyTrafficChart);
        activeDevicesLineChart = findViewById(R.id.activeDevicesLineChart);
        categoryPieChart = findViewById(R.id.categoryPieChart);
        sourcePieChart = findViewById(R.id.sourcePieChart);

        btnStartTracker = findViewById(R.id.btnStartTracker);
        Button btnLiveDevices = findViewById(R.id.btnLiveDevices);
        Button btnExportData = findViewById(R.id.btnExportData);
        Button btnDebugLog = findViewById(R.id.btnDebugLog);
        Button btnSyncData = findViewById(R.id.btnSyncData);

        btnStartTracker.setOnClickListener(v -> {
            if (isTracking) {
                stopScannerService();
            } else {
                // Show confirmation before wiping all historical data
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Start New Session")
                        .setMessage("This will permanently delete all previously recorded data. Continue?")
                        .setPositiveButton("Start", (dialog, which) -> startNewSession())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        btnLiveDevices.setOnClickListener(v -> startActivity(new Intent(this, NearbyDevicesActivity.class)));
        btnExportData.setOnClickListener(v -> exportData());
        btnDebugLog.setOnClickListener(v -> startActivity(new Intent(this, DebugLogActivity.class)));
        btnSyncData.setOnClickListener(v -> triggerManualSync());

        trackerExecutor = Executors.newSingleThreadExecutor();

        setupCharts();

        // Sync button to restored tracking state before any async work
        updateTrackerButton();

        DashboardViewModel viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getMetrics().observe(this, this::updateUI);

        checkAndRequestPermissions();
    }

    // Request codes for the two-stage permission flow
    private static final int REQ_CORE_PERMISSIONS       = 1001;
    private static final int REQ_BACKGROUND_LOCATION    = 1002;
    private static final int REQ_POST_NOTIFICATIONS     = 1003;

    /**
     * Stage 1 — core permissions: Bluetooth + fine/coarse location + notifications (API 33+).
     * ACCESS_BACKGROUND_LOCATION is intentionally excluded here; it must be requested
     * in its own separate dialog AFTER fine location is already granted (Android 11+ rule).
     */
    private void checkAndRequestPermissions() {
        List<String> missing = new ArrayList<>();

        // Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        // Location permissions (always needed for BT + Wi-Fi scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Notification permission (Android 13+) — safe to include in stage-1 batch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_CORE_PERMISSIONS);
        } else {
            // Core permissions already granted — proceed to background location, then start
            requestBackgroundLocationIfNeeded();
        }
    }

    /**
     * Stage 2 — background location.
     * Must be requested AFTER fine location is granted (separate dialog, separate request code).
     */
    private void requestBackgroundLocationIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND_LOCATION);
            return;
        }
        // Background location granted (or not needed) — safe to start service
        if (isTracking) {
            startScannerService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CORE_PERMISSIONS) {
            // Check that the critical permissions (fine location + BT on API 31+) were granted.
            // POST_NOTIFICATIONS denial is non-fatal — scanning still works without it.
            boolean fineLocationGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (!fineLocationGranted) {
                Toast.makeText(this,
                        "Location permission is required for Bluetooth and Wi-Fi scanning.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            // Core permissions OK — request background location next
            requestBackgroundLocationIfNeeded();

        } else if (requestCode == REQ_BACKGROUND_LOCATION || requestCode == REQ_POST_NOTIFICATIONS) {
            // Background location denial is non-fatal — foreground scanning still works.
            // Either way, proceed to start the service if tracking was requested.
            if (isTracking) {
                startScannerService();
            }
        }
    }

    private void startScannerService() {
        Intent serviceIntent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        startForegroundService(serviceIntent);
        isTracking = true;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_TRACKING, true).apply();
        updateTrackerButton();
    }

    private void stopScannerService() {
        // Use stopService() — NOT startForegroundService() — to stop the service.
        // Sending ACTION_STOP via startForegroundService re-creates the service if it
        // was already killed by the OS, which is the opposite of what we want here.
        Intent serviceIntent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        stopService(serviceIntent);
        isTracking = false;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_TRACKING, false).apply();
        updateTrackerButton();
    }

    private void startNewSession() {
        AppDatabase db = AppDatabase.getDatabase(this);
        trackerExecutor.execute(() -> {
            try {
                db.clearAllTables();
                getSharedPreferences("sync_prefs", MODE_PRIVATE).edit()
                        .remove("last_synced_obs_id")
                        .remove("last_synced_session_id")
                        .apply();
                runOnUiThread(() -> {
                    // Only mark as tracking after the database has been successfully cleared
                    isTracking = true;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putBoolean(KEY_IS_TRACKING, true).apply();
                    Toast.makeText(this, "Previous data cleared. New tracking session started.", Toast.LENGTH_LONG).show();
                    // Route through the permission flow — if permissions were revoked since
                    // last launch, this will re-request them before starting the service.
                    checkAndRequestPermissions();
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to clear database for new session", e);
                runOnUiThread(() ->
                    Toast.makeText(this, "Error starting new session: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void updateTrackerButton() {
        if (isTracking) {
            btnStartTracker.setText(R.string.btn_stop_tracker);
            btnStartTracker.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#CF6679")));
            btnStartTracker.setTextColor(Color.WHITE);
        } else {
            btnStartTracker.setText(R.string.btn_start_tracker);
            btnStartTracker.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#B3C4C1")));
            btnStartTracker.setTextColor(Color.parseColor("#1E1E1E"));
        }
    }

    private void setupCharts() {
        hourlyTrafficChart.getDescription().setEnabled(false);
        hourlyTrafficChart.getLegend().setTextColor(Color.WHITE);
        hourlyTrafficChart.getAxisLeft().setTextColor(Color.WHITE);
        hourlyTrafficChart.getAxisRight().setEnabled(false);
        XAxis xAxis = hourlyTrafficChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);

        activeDevicesLineChart.getDescription().setEnabled(false);
        activeDevicesLineChart.getLegend().setTextColor(Color.WHITE);
        activeDevicesLineChart.getAxisLeft().setTextColor(Color.WHITE);
        activeDevicesLineChart.getAxisRight().setEnabled(false);
        XAxis lineXAxis = activeDevicesLineChart.getXAxis();
        lineXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        lineXAxis.setTextColor(Color.WHITE);
        lineXAxis.setDrawGridLines(false);

        setupPieChart(categoryPieChart);
        setupPieChart(sourcePieChart);
    }

    private void setupPieChart(PieChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.parseColor("#222222"));
        chart.setTransparentCircleRadius(0f);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.setEntryLabelColor(Color.WHITE);
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private void updateUI(FootfallMetrics metrics) {
        if (metrics == null) return;

        tvCurrentDevices.setText(String.valueOf(metrics.currentNearbyDevices));
        tvTotalVisitors.setText(String.valueOf(metrics.totalVisitorsToday));
        tvReturningVisitors.setText(String.valueOf(metrics.returningVisitors));

        long minutes = metrics.averageDwellTimeMs / (1000 * 60);
        tvAvgDwellTime.setText(minutes + "m");

        tvPeakHour.setText(metrics.peakActivityMinsAgo);

        updateBarChart(metrics.hourlyTrafficTrend);
        updateLineChart(metrics.last5MinutesTrend);
        updatePieChart(categoryPieChart, metrics.categoryDistribution);
        updatePieChart(sourcePieChart, metrics.deviceSourceDistribution);
    }

    private void updateBarChart(Map<Integer, Integer> hourlyTrend) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            Integer value = hourlyTrend.get(i);
            entries.add(new BarEntry(i, value == null ? 0 : value));
        }
        BarDataSet dataSet = new BarDataSet(entries, "Visitors per Hour");
        dataSet.setColor(Color.parseColor("#B3C4C1"));
        dataSet.setValueTextColor(Color.WHITE);
        hourlyTrafficChart.setData(new BarData(dataSet));
        hourlyTrafficChart.invalidate();
    }

    private void updateLineChart(Map<Integer, Integer> minuteTrend) {
        if (minuteTrend == null) return;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Integer value = minuteTrend.get(i);
            entries.add(new Entry(i, value == null ? 0 : value));
        }
        LineDataSet dataSet = new LineDataSet(entries, "Active Devices");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#FF9800"));
        activeDevicesLineChart.setData(new LineData(dataSet));
        activeDevicesLineChart.invalidate();
    }

    private void updatePieChart(PieChart chart, Map<String, Integer> distribution) {
        if (distribution == null) return;
        List<PieEntry> entries = new ArrayList<>();
        int[] colors = {
                Color.parseColor("#E91E63"),
                Color.parseColor("#FF9800"),
                Color.parseColor("#FFC107"),
                Color.parseColor("#4CAF50"),
                Color.parseColor("#00BCD4")
        };
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            }
        }
        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);
        chart.setData(new PieData(dataSet));
        chart.invalidate();
    }

    private void exportData() {
        String report = "Billboard Analytics Export\n"
                + "==========================\n"
                + "Total Visitors Today: " + tvTotalVisitors.getText() + "\n"
                + "Current Nearby Devices: " + tvCurrentDevices.getText() + "\n"
                + "Returning Visitors: " + tvReturningVisitors.getText() + "\n"
                + "Average Dwell Time: " + tvAvgDwellTime.getText() + "\n"
                + "Peak Hour Activity: " + tvPeakHour.getText() + "\n";

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Export Analytics Data"));
    }

    private void triggerManualSync() {
        if (!isTracking) {
            Toast.makeText(this, "Start tracking first to sync data.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        intent.setAction(com.example.billboardanalytics.service.ScannerService.ACTION_TRIGGER_SYNC);
        startForegroundService(intent);
        Toast.makeText(this, "Immediate Cloud Sync Triggered!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackerExecutor != null) {
            trackerExecutor.shutdownNow();
        }
    }
}
