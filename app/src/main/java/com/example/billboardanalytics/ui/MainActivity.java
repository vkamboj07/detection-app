package com.example.billboardanalytics.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.billboardanalytics.R;
import com.example.billboardanalytics.engine.FootfallMetrics;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
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

    private TextView tvCurrentDevices, tvTotalVisitors, tvReturningVisitors, tvAvgDwellTime, tvPeakHour;
    private BarChart hourlyTrafficChart;
    private PieChart sourcePieChart;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCurrentDevices = findViewById(R.id.tvCurrentDevices);
        tvTotalVisitors = findViewById(R.id.tvTotalVisitors);
        tvReturningVisitors = findViewById(R.id.tvReturningVisitors);
        tvAvgDwellTime = findViewById(R.id.tvAvgDwellTime);
        tvPeakHour = findViewById(R.id.tvPeakHour);
        
        hourlyTrafficChart = findViewById(R.id.hourlyTrafficChart);
        sourcePieChart = findViewById(R.id.sourcePieChart);

        Button btnLiveDevices = findViewById(R.id.btnLiveDevices);
        btnLiveDevices.setOnClickListener(v -> startActivity(new Intent(this, NearbyDevicesActivity.class)));

        setupCharts();

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
                startScannerService();
            }
        }
    }

    private void startScannerService() {
        Intent serviceIntent = new Intent(this, com.example.billboardanalytics.service.ScannerService.class);
        startForegroundService(serviceIntent);
    }

    private void setupCharts() {
        // Bar Chart Setup
        hourlyTrafficChart.getDescription().setEnabled(false);
        hourlyTrafficChart.getLegend().setTextColor(Color.WHITE);
        hourlyTrafficChart.getAxisLeft().setTextColor(Color.WHITE);
        hourlyTrafficChart.getAxisRight().setEnabled(false);
        XAxis xAxis = hourlyTrafficChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        
        // Pie Chart Setup
        sourcePieChart.getDescription().setEnabled(false);
        sourcePieChart.setDrawHoleEnabled(true);
        sourcePieChart.setHoleColor(Color.parseColor("#1E1E1E"));
        sourcePieChart.setTransparentCircleRadius(0f);
        sourcePieChart.getLegend().setTextColor(Color.WHITE);
        sourcePieChart.setEntryLabelColor(Color.WHITE);
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private void updateUI(FootfallMetrics metrics) {
        if (metrics == null) return;

        tvCurrentDevices.setText(String.valueOf(metrics.currentNearbyDevices));
        tvTotalVisitors.setText(String.valueOf(metrics.totalVisitorsToday));
        tvReturningVisitors.setText(String.valueOf(metrics.returningVisitors));
        
        long minutes = metrics.averageDwellTimeMs / (1000 * 60);
        tvAvgDwellTime.setText(minutes + "m");
        
        tvPeakHour.setText(metrics.peakHour);

        updateBarChart(metrics.hourlyTrafficTrend);
        updatePieChart(metrics.deviceSourceDistribution);
    }

    private void updateBarChart(Map<Integer, Integer> hourlyTrend) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            Integer value = hourlyTrend.get(i);
            entries.add(new BarEntry(i, value == null ? 0 : value));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Visitors per Hour");
        dataSet.setColor(Color.parseColor("#03DAC6"));
        dataSet.setValueTextColor(Color.WHITE);

        BarData barData = new BarData(dataSet);
        hourlyTrafficChart.setData(barData);
        hourlyTrafficChart.invalidate();
    }

    private void updatePieChart(Map<String, Integer> sourceDistribution) {
        List<PieEntry> entries = new ArrayList<>();
        int[] colors = new int[]{
                Color.parseColor("#BB86FC"), // BLE
                Color.parseColor("#03DAC6"), // WIFI
                Color.parseColor("#CF6679")  // BT_CLASSIC
        };

        for (Map.Entry<String, Integer> entry : sourceDistribution.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue(), entry.getKey()));
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "Sources");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);

        PieData pieData = new PieData(dataSet);
        sourcePieChart.setData(pieData);
        sourcePieChart.invalidate();
    }
}
