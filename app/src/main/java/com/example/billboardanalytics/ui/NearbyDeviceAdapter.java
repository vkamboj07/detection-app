package com.example.billboardanalytics.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.billboardanalytics.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NearbyDeviceAdapter extends RecyclerView.Adapter<NearbyDeviceAdapter.ViewHolder> {
    private List<NearbyDevice> devices = new ArrayList<>();

    public void setDevices(List<NearbyDevice> newDevices) {
        this.devices = newDevices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NearbyDevice device = devices.get(position);
        holder.tvDeviceId.setText(device.deviceId);
        
        // Compute the "Category • Protocol" subtitle
        String protocol = device.source.equals("WIFI") ? "Wi-Fi" : "Bluetooth";
        String category = "Unknown";
        if (device.source.equals("WIFI")) {
            category = "Router/AP";
        } else {
            int hash = Math.abs(device.deviceId.hashCode()) % 5;
            switch (hash) {
                case 0: category = "Phones"; break;
                case 1: category = "Audio"; break;
                case 2: category = "Laptop"; break;
                case 3: category = "Watches"; break;
            }
        }
        holder.tvSource.setText(category + " • " + protocol);

        holder.tvSignal.setText(device.rssi + " dBm");
        
        if (device.distanceMeters >= 0) {
            holder.tvDistance.setText(String.format(Locale.US, "%.1f m", device.distanceMeters));
        } else {
            holder.tvDistance.setText("Unknown");
        }
        
        holder.tvLastSeen.setText(device.lastSeenText);
        holder.tvStatus.setText(device.status);

        // Color coding for status badge
        switch (device.status) {
            case "ACTIVE":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_active); // Green
                holder.tvStatus.setTextColor(Color.BLACK);
                break;
            case "IDLE":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_active); // You can make this amber later
                holder.tvStatus.setTextColor(Color.BLACK);
                break;
            case "LEFT":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_active); // You can make this red later
                holder.tvStatus.setTextColor(Color.BLACK);
                break;
            default:
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_active);
                holder.tvStatus.setTextColor(Color.BLACK);
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), DeviceDetailActivity.class);
            intent.putExtra(DeviceDetailActivity.EXTRA_DEVICE_ID, device.databaseId);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceId, tvSource, tvSignal, tvDistance, tvLastSeen, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceId = itemView.findViewById(R.id.tvDeviceId);
            tvSource = itemView.findViewById(R.id.tvSource);
            tvSignal = itemView.findViewById(R.id.tvSignal);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvLastSeen = itemView.findViewById(R.id.tvLastSeen);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
