package com.example.billboardanalytics.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.billboardanalytics.R;
import com.example.billboardanalytics.data.ObservationEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DeviceDetailActivity extends AppCompatActivity {
    public static final String EXTRA_DEVICE_ID = "extra_device_id";

    private TextView tvDetailTitle, tvFirstSeen, tvLastSeen, tvDetections, tvAvgRssi, tvSessions, tvTotalDwell;
    private LineChart rssiChart;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvFirstSeen = findViewById(R.id.tvFirstSeen);
        tvLastSeen = findViewById(R.id.tvLastSeen);
        tvDetections = findViewById(R.id.tvDetections);
        tvAvgRssi = findViewById(R.id.tvAvgRssi);
        tvSessions = findViewById(R.id.tvSessions);
        tvTotalDwell = findViewById(R.id.tvTotalDwell);
        rssiChart = findViewById(R.id.rssiChart);

        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        setupChart();

        long deviceId = getIntent().getLongExtra(EXTRA_DEVICE_ID, -1);
        
        DeviceDetailViewModel viewModel = new ViewModelProvider(this).get(DeviceDetailViewModel.class);
        viewModel.getDetailData().observe(this, this::populateUI);

        if (deviceId != -1) {
            viewModel.loadDeviceDetails(deviceId);
        }
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private void populateUI(DeviceDetailViewModel.DeviceDetailData data) {
        tvDetailTitle.setText("Device: " + data.anonymousId);
        tvFirstSeen.setText(data.firstSeen);
        tvLastSeen.setText(data.lastSeen);
        tvDetections.setText(String.valueOf(data.detectionCount));
        tvAvgRssi.setText(data.averageRssi + " dBm");
        tvSessions.setText(String.valueOf(data.sessionCount));
        tvTotalDwell.setText(data.totalDwellTime);

        updateChart(data.observations);
    }

    private void setupChart() {
        rssiChart.getDescription().setEnabled(false);
        rssiChart.setTouchEnabled(true);
        rssiChart.setDragEnabled(true);
        rssiChart.setScaleEnabled(true);
        rssiChart.setPinchZoom(true);
        rssiChart.getLegend().setTextColor(Color.WHITE);

        XAxis xAxis = rssiChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);

        rssiChart.getAxisLeft().setTextColor(Color.WHITE);
        rssiChart.getAxisRight().setEnabled(false);
    }

    private void updateChart(List<ObservationEntity> observations) {
        if (observations == null || observations.isEmpty()) return;

        // Ensure chronological order
        List<ObservationEntity> sorted = new ArrayList<>(observations);
        sorted.sort(Comparator.comparing(o -> o.timestamp));

        long baseTime = parseTimestamp(sorted.get(0).timestamp);
        
        List<Entry> entries = new ArrayList<>();
        for (ObservationEntity obs : sorted) {
            long timeMs = parseTimestamp(obs.timestamp);
            float x = (timeMs - baseTime) / 1000f; // Seconds from first observation
            entries.add(new Entry(x, obs.rssi));
        }

        LineDataSet dataSet = new LineDataSet(entries, "RSSI (dBm)");
        dataSet.setColor(Color.parseColor("#BB86FC"));
        dataSet.setCircleColor(Color.parseColor("#03DAC6"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        rssiChart.setData(lineData);
        rssiChart.invalidate(); // refresh
    }

    private long parseTimestamp(String timestamp) {
        try {
            Date date = dateFormat.parse(timestamp);
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }
}
