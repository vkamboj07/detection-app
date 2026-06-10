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
        holder.tvSource.setText(device.source);
        holder.tvSignal.setText(device.rssi + " dBm");
        
        if (device.distanceMeters >= 0) {
            holder.tvDistance.setText(String.format(Locale.US, "%.1f m", device.distanceMeters));
        } else {
            holder.tvDistance.setText("Unknown");
        }
        
        holder.tvLastSeen.setText(device.lastSeenText);
        holder.tvStatus.setText(device.status);

        // Color coding for status
        switch (device.status) {
            case "ACTIVE":
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
                break;
            case "IDLE":
                holder.tvStatus.setTextColor(Color.parseColor("#FFC107")); // Amber
                break;
            case "LEFT":
                holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
                break;
            default:
                holder.tvStatus.setTextColor(Color.parseColor("#AAAAAA"));
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
