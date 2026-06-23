package com.example.billboardanalytics.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.billboardanalytics.R;
import com.footfallanalytics.sdk.util.DeviceCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class NearbyDeviceAdapter extends RecyclerView.Adapter<NearbyDeviceAdapter.ViewHolder> {
    private List<NearbyDevice> devices = new ArrayList<>();
    private OnDeviceClickListener clickListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(long databaseId);
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Updates the list using DiffUtil so only changed rows are redrawn.
     * Replaces the old notifyDataSetChanged() which caused full-list flicker every 2 seconds.
     */
    public void setDevices(List<NearbyDevice> newDevices) {
        final List<NearbyDevice> oldDevices = this.devices;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return oldDevices.size(); }

            @Override
            public int getNewListSize() { return newDevices.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                // Items represent the same device if their database IDs match
                return oldDevices.get(oldPos).databaseId == newDevices.get(newPos).databaseId;
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                NearbyDevice o = oldDevices.get(oldPos);
                NearbyDevice n = newDevices.get(newPos);
                // Redraw only if visible fields changed
                return o.rssi == n.rssi
                        && Objects.equals(o.status, n.status)
                        && Objects.equals(o.lastSeenText, n.lastSeenText)
                        && (Math.abs(o.distanceMeters - n.distanceMeters) < 0.05);
            }

            @NonNull
            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                return Boolean.TRUE; // non-null payload suppresses the default fade animation
            }
        });
        this.devices = newDevices;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_device, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NearbyDevice device = devices.get(position);
        
        holder.tvDeviceId.setText(device.deviceId);
        
        // Compute the "Category • Protocol" subtitle
        String protocol = "WIFI".equals(device.source) ? "Wi-Fi" : "Bluetooth";
        String category = DeviceCategory.resolve(device.source, device.deviceId);
        holder.tvSource.setText(category + " • " + protocol);

        holder.tvSignal.setText(device.rssi + " dBm");
        
        if (device.distanceMeters >= 0) {
            holder.tvDistance.setText(String.format(Locale.US, "%.1f m", device.distanceMeters));
        } else {
            holder.tvDistance.setText("Unknown");
        }
        
        holder.tvLastSeen.setText(device.lastSeenText);
        holder.tvStatus.setText(device.status);

        // Color coding for status badge — all states use shaped drawables to preserve rounded corners
        switch (device.status) {
            case "IDLE":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_idle);
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            case "LEFT":
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_left);
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            default: // ACTIVE
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_active);
                holder.tvStatus.setTextColor(Color.BLACK);
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onDeviceClick(device.databaseId);
            }
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
