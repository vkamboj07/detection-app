package com.example.billboardanalytics.ui;

import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;

import java.util.List;
import java.util.concurrent.Executors;

public class DebugLogActivity extends AppCompatActivity {

    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build layout programmatically to avoid needing a new XML
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        root.setPadding(48, 48, 48, 48);

        // Header row
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView btnBack = new ImageView(this);
        btnBack.setImageResource(android.R.drawable.ic_menu_revert);
        btnBack.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(80, 80);
        backParams.setMarginEnd(32);
        btnBack.setLayoutParams(backParams);
        btnBack.setClickable(true);
        btnBack.setFocusable(true);
        btnBack.setOnClickListener(v -> finish());

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Debug Log");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(22);

        header.addView(btnBack);
        header.addView(tvTitle);

        // Log output
        ScrollView scrollView = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setTextColor(Color.parseColor("#B0B0B0"));
        tvLog.setTextSize(12);
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setPadding(0, 32, 0, 0);
        scrollView.addView(tvLog);

        root.addView(header);
        root.addView(scrollView);
        setContentView(root);

        loadDebugInfo();
    }

    private void loadDebugInfo() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            List<DeviceEntity> devices = db.analyticsDao().getAllDevices();

            StringBuilder sb = new StringBuilder();
            sb.append("=== Database Summary ===\n");
            sb.append("Total devices: ").append(devices.size()).append("\n\n");

            for (DeviceEntity device : devices) {
                sb.append("ID: ").append(device.id).append("\n");
                sb.append("  Identifier: ").append(device.deviceIdentifier).append("\n");
                sb.append("  Source: ").append(device.source).append("\n");
                sb.append("  First Seen: ").append(device.firstSeen).append("\n");
                sb.append("  Last Seen: ").append(device.lastSeen).append("\n");

                List<ObservationEntity> obs = db.analyticsDao().getObservationsForDevice(device.id);
                sb.append("  Observations: ").append(obs.size()).append("\n\n");
            }

            String log = sb.toString();
            runOnUiThread(() -> tvLog.setText(log));
        });
    }
}
