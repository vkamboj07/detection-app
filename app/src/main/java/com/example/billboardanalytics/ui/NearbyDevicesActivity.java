package com.example.billboardanalytics.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.billboardanalytics.R;

public class NearbyDevicesActivity extends AppCompatActivity {
    private static final String TAG = "NearbyDevicesActivity";
    private NearbyDeviceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_devices);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rvNearbyDevices = findViewById(R.id.rvNearbyDevices);
        rvNearbyDevices.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NearbyDeviceAdapter();
        adapter.setOnDeviceClickListener(databaseId -> {
            Log.d(TAG, "Device clicked, databaseId=" + databaseId);
            Intent intent = new Intent(NearbyDevicesActivity.this, DeviceDetailActivity.class);
            intent.putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, databaseId);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        rvNearbyDevices.setAdapter(adapter);

        NearbyDevicesViewModel viewModel = new ViewModelProvider(this).get(NearbyDevicesViewModel.class);

        viewModel.getNearbyDevices().observe(this, devices -> {
            if (devices != null) {
                adapter.setDevices(devices);
            }
        });
    }
}
