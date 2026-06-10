package com.example.billboardanalytics.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "sessions",
    foreignKeys = @ForeignKey(
        entity = DeviceEntity.class,
        parentColumns = "id",
        childColumns = "device_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = {"device_id"})}
)
public class SessionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "device_id")
    public long deviceId;

    @ColumnInfo(name = "start_time")
    public String startTime; // ISO-8601 Timestamp

    @ColumnInfo(name = "end_time")
    public String endTime; // ISO-8601 Timestamp

    @ColumnInfo(name = "duration")
    public long duration; // in milliseconds
}
