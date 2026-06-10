package com.example.billboardanalytics.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.billboardanalytics.R;

public class NearbyDevicesActivity extends AppCompatActivity {
    private NearbyDeviceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_devices);

        RecyclerView rvNearbyDevices = findViewById(R.id.rvNearbyDevices);
        rvNearbyDevices.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new NearbyDeviceAdapter();
        rvNearbyDevices.setAdapter(adapter);

        NearbyDevicesViewModel viewModel = new ViewModelProvider(this).get(NearbyDevicesViewModel.class);
        
        viewModel.getNearbyDevices().observe(this, devices -> {
            if (devices != null) {
                adapter.setDevices(devices);
            }
        });
    }
}
