package com.example.billboardanalytics.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AnalyticsDao {

    // --- Devices ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertDevice(DeviceEntity device);

    @Query("SELECT * FROM devices WHERE device_identifier = :identifier LIMIT 1")
    DeviceEntity getDeviceByIdentifier(String identifier);
    
    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    DeviceEntity getDeviceById(long id);

    @Update
    void updateDevice(DeviceEntity device);

    // --- Observations ---
    @Insert
    void insertObservation(ObservationEntity observation);

    @Query("SELECT * FROM observations WHERE device_id = :deviceId ORDER BY timestamp DESC")
    List<ObservationEntity> getObservationsForDevice(long deviceId);

    // --- Sessions ---
    @Insert
    long insertSession(SessionEntity session);

    @Update
    void updateSession(SessionEntity session);

    @Query("SELECT * FROM sessions WHERE device_id = :deviceId ORDER BY start_time DESC LIMIT 1")
    SessionEntity getLatestSessionForDevice(long deviceId);
    
    @Query("SELECT * FROM sessions WHERE device_id = :deviceId ORDER BY start_time ASC")
    List<SessionEntity> getAllSessionsForDevice(long deviceId);
    
    @Query("SELECT COUNT(*) FROM observations WHERE device_id = :deviceId AND timestamp >= :startTime AND timestamp <= :endTime")
    int getObservationCountForSession(long deviceId, String startTime, String endTime);

    // --- Analytics ---
    @Query("SELECT * FROM sessions WHERE start_time >= :startOfDay AND start_time <= :endOfDay")
    List<SessionEntity> getSessionsForDateRange(String startOfDay, String endOfDay);

    @Query("SELECT COUNT(DISTINCT device_id) FROM observations WHERE timestamp >= :timestamp")
    int getNearbyDevicesCount(String timestamp);
    
    @Query("SELECT * FROM devices")
    List<DeviceEntity> getAllDevices();

}
