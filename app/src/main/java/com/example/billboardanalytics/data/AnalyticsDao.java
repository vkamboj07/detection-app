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

    /**
     * Returns up to {@code limit} observations with id > {@code afterId}, ordered ascending.
     * Used by SupabaseSyncManager to fetch only rows not yet uploaded.
     */
    @Query("SELECT * FROM observations WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    List<ObservationEntity> getObservationsAfter(long afterId, int limit);

    // --- Sessions ---
    @Insert
    long insertSession(SessionEntity session);

    @Update
    void updateSession(SessionEntity session);

    @Query("SELECT * FROM sessions WHERE device_id = :deviceId ORDER BY start_time DESC LIMIT 1")
    SessionEntity getLatestSessionForDevice(long deviceId);
    
    @Query("SELECT * FROM sessions WHERE device_id = :deviceId ORDER BY start_time ASC")
    List<SessionEntity> getAllSessionsForDevice(long deviceId);

    /**
     * Returns up to {@code limit} sessions with id > {@code afterId}, ordered ascending.
     * Used by SupabaseSyncManager to fetch only rows not yet uploaded.
     */
    @Query("SELECT * FROM sessions WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    List<SessionEntity> getSessionsAfter(long afterId, int limit);
    
    @Query("SELECT COUNT(*) FROM observations WHERE device_id = :deviceId AND timestamp >= :startTime AND timestamp <= :endTime")
    int getObservationCountForSession(long deviceId, String startTime, String endTime);

    // --- Analytics ---
    @Query("SELECT * FROM sessions WHERE start_time >= :startOfDay AND start_time <= :endOfDay")
    List<SessionEntity> getSessionsForDateRange(String startOfDay, String endOfDay);

    @Query("SELECT COUNT(DISTINCT device_id) FROM observations WHERE timestamp >= :timestamp")
    int getNearbyDevicesCount(String timestamp);
    
    @Query("SELECT * FROM devices")
    List<DeviceEntity> getAllDevices();

    /**
     * Returns the single most-recent observation for every device that has been
     * seen since {@code sinceTimestamp} (ISO-8601 string).  One SQL query instead
     * of N per-device queries, eliminating the N+1 problem in NearbyDevicesViewModel.
     */
    @Query("SELECT o.* FROM observations o " +
           "INNER JOIN (SELECT device_id, MAX(timestamp) AS max_ts FROM observations GROUP BY device_id) latest " +
           "ON o.device_id = latest.device_id AND o.timestamp = latest.max_ts " +
           "INNER JOIN devices d ON o.device_id = d.id " +
           "WHERE d.last_seen >= :sinceTimestamp")
    List<ObservationEntity> getLatestObservationPerDeviceSince(String sinceTimestamp);

}
