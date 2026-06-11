package com.example.billboardanalytics.ui;

import android.content.Intent;
import android.content.SharedPreferences;
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

import android.Manifest;
import android.content.pm.PackageManager;
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

        btnStartTracker.setOnClickListener(v -> toggleTracker());
        btnLiveDevices.setOnClickListener(v -> startActivity(new Intent(this, NearbyDevicesActivity.class)));
        btnExportData.setOnClickListener(v -> exportData());
        btnDebugLog.setOnClickListener(v -> startActivity(new Intent(this, DebugLogActivity.class)));

        setupCharts();

        // Sync button to restored tracking state before any async work
        updateTrackerButton();

        DashboardViewModel viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getMetrics().observe(this, this::updateUI);

        checkAndRequestPermissions();
    }

    @android.annotation.SuppressLint("InlinedApi")
    @NonNull
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    private void checkAndRequestPermissions() {
        String[] requiredPermissions = getRequiredPermissions();

        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), 1001);
        } else {
            startScannerService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // startScannerService() is guarded against double-starts internally
                startScannerService();
            }
        }
    }

    private void startScannerService() {
        // Guard: don't double-start if the service is already running
        if (isTracking) return;

        Intent serviceIntent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        startForegroundService(serviceIntent);
        isTracking = true;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_TRACKING, true).apply();
        updateTrackerButton();
    }

    private void stopScannerService() {
        Intent serviceIntent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        stopService(serviceIntent);
        isTracking = false;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_IS_TRACKING, false).apply();
        updateTrackerButton();
    }

    private void toggleTracker() {
        if (isTracking) {
            stopScannerService();
        } else {
            // Start a new session: clear data then start service
            AppDatabase db = AppDatabase.getDatabase(this);
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                db.clearAllTables();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Previous data cleared. New tracking session started.", Toast.LENGTH_LONG).show();
                    startScannerService();
                });
            });
        }
    }

    private void updateTrackerButton() {
        if (isTracking) {
            btnStartTracker.setText("Stop Tracker");
            btnStartTracker.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#CF6679")));
            btnStartTracker.setTextColor(Color.WHITE);
        } else {
            btnStartTracker.setText("Start Tracker");
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
}
