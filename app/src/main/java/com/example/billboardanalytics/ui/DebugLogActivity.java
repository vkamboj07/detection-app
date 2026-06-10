package com.example.billboardanalytics.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.billboardanalytics.R;
import com.example.billboardanalytics.data.AppDatabase;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.SessionEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebugLogActivity extends AppCompatActivity {

    private TextView tvJsonOutput;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);

        tvJsonOutput = findViewById(R.id.tvJsonOutput);
        db = AppDatabase.getDatabase(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        findViewById(R.id.btnLoadJson).setOnClickListener(v -> loadJsonData());
        
        findViewById(R.id.btnClearDb).setOnClickListener(v -> clearDatabase());
    }

    private void loadJsonData() {
        executor.execute(() -> {
            try {
                List<DeviceEntity> devices = db.analyticsDao().getAllDevices();
                JSONArray jsonArray = new JSONArray();

                for (DeviceEntity device : devices) {
                    JSONObject deviceJson = new JSONObject();
                    deviceJson.put("id", device.id);
                    deviceJson.put("device_identifier", device.deviceIdentifier);
                    deviceJson.put("source", device.source);
                    deviceJson.put("first_seen", device.firstSeen);
                    deviceJson.put("last_seen", device.lastSeen);

                    List<SessionEntity> sessions = db.analyticsDao().getAllSessionsForDevice(device.id);
                    JSONArray sessionsArray = new JSONArray();
                    for (SessionEntity session : sessions) {
                        JSONObject sessionJson = new JSONObject();
                        sessionJson.put("session_id", session.id);
                        sessionJson.put("start_time", session.startTime);
                        sessionJson.put("end_time", session.endTime);
                        sessionJson.put("duration_ms", session.duration);
                        sessionsArray.put(sessionJson);
                    }
                    deviceJson.put("sessions", sessionsArray);
                    jsonArray.put(deviceJson);
                }

                String jsonOutput = jsonArray.toString(4);
                runOnUiThread(() -> tvJsonOutput.setText(jsonOutput));

            } catch (JSONException e) {
                runOnUiThread(() -> tvJsonOutput.setText("Error generating JSON:\n" + e.getMessage()));
            }
        });
    }

    private void clearDatabase() {
        executor.execute(() -> {
            db.clearAllTables();
            runOnUiThread(() -> {
                Toast.makeText(this, "Database Cleared", Toast.LENGTH_SHORT).show();
                tvJsonOutput.setText("Database cleared. No data to show.");
            });
        });
    }
}
