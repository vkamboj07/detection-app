package com.example.billboardanalytics.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "devices",
    indices = {@Index(value = {"device_identifier"}, unique = true)}
)
public class DeviceEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "device_identifier")
    public String deviceIdentifier; // MAC or BSSID

    @ColumnInfo(name = "source")
    public String source; // WIFI, BLE, BT_CLASSIC

    @ColumnInfo(name = "first_seen")
    public String firstSeen; // ISO-8601 Timestamp

    @ColumnInfo(name = "last_seen")
    public String lastSeen; // ISO-8601 Timestamp
}
