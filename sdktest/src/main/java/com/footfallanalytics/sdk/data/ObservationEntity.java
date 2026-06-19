package com.footfallanalytics.sdk.data;

import androidx.annotation.RestrictTo;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@RestrictTo(RestrictTo.Scope.LIBRARY)
@Entity(
    tableName = "observations",
    foreignKeys = @ForeignKey(
        entity = DeviceEntity.class,
        parentColumns = "id",
        childColumns = "device_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = {"device_id"})}
)
public class ObservationEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "device_id")
    public long deviceId;

    @ColumnInfo(name = "timestamp")
    public String timestamp;

    @ColumnInfo(name = "rssi")
    public int rssi;

    @ColumnInfo(name = "source")
    public String source;
}
