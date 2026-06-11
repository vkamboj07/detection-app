package com.example.billboardanalytics.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.billboardanalytics.R;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;
import com.example.billboardanalytics.data.SessionEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class DebugLogActivity extends AppCompatActivity {

    private TextView tvJsonOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvJsonOutput = findViewById(R.id.tvJsonOutput);

        Button btnLoadJson = findViewById(R.id.btnLoadJson);
        Button btnClearDb = findViewById(R.id.btnClearDb);

        btnLoadJson.setOnClickListener(v -> loadSessionHistoryJson());
        btnClearDb.setOnClickListener(v -> clearDatabase());

        // Load on open
        loadSessionHistoryJson();
    }

    private void loadSessionHistoryJson() {
        tvJsonOutput.setText("Loading...");
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(getApplicationContext());
            List<DeviceEntity> devices = db.analyticsDao().getAllDevices();

            // Build a JSON-friendly structure per device
            List<Map<String, Object>> output = new ArrayList<>();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            for (DeviceEntity device : devices) {
                List<ObservationEntity> observations = db.analyticsDao().getObservationsForDevice(device.id);
                List<SessionEntity> sessions = db.analyticsDao().getAllSessionsForDevice(device.id);

                Map<String, Object> deviceMap = new HashMap<>();
                deviceMap.put("id", device.id);
                deviceMap.put("identifier", device.deviceIdentifier);
                deviceMap.put("source", device.source);
                deviceMap.put("first_seen", device.firstSeen);
                deviceMap.put("last_seen", device.lastSeen);
                deviceMap.put("observation_count", observations.size());
                deviceMap.put("session_count", sessions.size());
                deviceMap.put("sessions", sessions);
                output.add(deviceMap);
            }

            String json = output.isEmpty()
                    ? "No data yet. Start the tracker to collect observations."
                    : gson.toJson(output);

            runOnUiThread(() -> tvJsonOutput.setText(json));
        });
    }

    private void clearDatabase() {
        tvJsonOutput.setText("Clearing database...");
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getDatabase(getApplicationContext()).clearAllTables();
            runOnUiThread(() -> {
                tvJsonOutput.setText("Database cleared.");
                Toast.makeText(this, "All data cleared.", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
