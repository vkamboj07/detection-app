package com.example.billboardanalytics.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;
import com.example.billboardanalytics.data.SessionEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Syncs locally-stored Room data (devices + observations + sessions) to the
 * Supabase REST API so the web dashboard receives live data from the Android scanner.
 *
 * Sync is debounced — multiple rapid calls to {@link #syncAsync()} within
 * {@code DEBOUNCE_SECONDS} are collapsed into a single upload, so BLE scans that fire
 * every few seconds don't flood the network.
 *
 * Sync is best-effort: failures are logged but never crash the app. The high-water
 * marks are only advanced after a confirmed successful HTTP response so no rows are lost.
 */
public class SupabaseSyncManager {

    private static final String TAG = "SupabaseSyncManager";
    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_LAST_SYNCED_OBS_ID     = "last_synced_obs_id";
    private static final String KEY_LAST_SYNCED_SESSION_ID = "last_synced_session_id";
    private static final int    BATCH_SIZE       = 50;
    private static final long   DEBOUNCE_SECONDS = 10;  // wait this long after last detection before uploading
    private static final long   MAX_WAIT_SECONDS = 30;  // but never delay more than this — guarantees upload
                                                         // even during continuous scanning

    private final AnalyticsDao    dao;
    private final SharedPreferences prefs;
    private final String          supabaseUrl;
    private final String          supabaseAnonKey;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingSync;
    // Tracks when the first syncAsync() call in the current burst arrived so we can
    // enforce MAX_WAIT_SECONDS even if new detections keep resetting the debounce.
    private long burstStartMs = 0;

    public SupabaseSyncManager(Context context, AnalyticsDao dao,
                               String supabaseUrl, String supabaseAnonKey) {
        this.dao            = dao;
        this.prefs          = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.supabaseUrl    = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    /**
     * Schedules an automatic background sync triggered by a new scan detection.
     *
     * Debounced: collapses rapid calls (every few seconds) into one upload.
     * Ceiling: guarantees a sync fires within MAX_WAIT_SECONDS of the first call
     * in a burst, so continuous scanning never starves the upload pipeline.
     */
    public synchronized void syncAsync() {
        long now = System.currentTimeMillis();

        if (pendingSync == null || pendingSync.isDone()) {
            // Start of a new burst — record when it began
            burstStartMs = now;
        }

        long msSinceBurstStart = now - burstStartMs;
        long remainingCeilingMs = (MAX_WAIT_SECONDS * 1000) - msSinceBurstStart;

        // How long to wait: the shorter of the debounce window and the remaining ceiling
        long delayMs = Math.min(DEBOUNCE_SECONDS * 1000, Math.max(0, remainingCeilingMs));

        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }
        try {
            pendingSync = scheduler.schedule(this::performSync, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "syncAsync rejected — scheduler is shut down");
        }
    }

    /**
     * Manual override: forces an immediate upload, bypassing the debounce.
     * Only call this from explicit user action (e.g. the "Sync Data" button).
     */
    public synchronized void syncImmediately() {
        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }
        burstStartMs = 0; // reset burst so next auto-sync starts fresh
        try {
            scheduler.execute(this::performSync);
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "syncImmediately rejected — scheduler is shut down");
        }
    }

    /** Call when the service is destroyed to release resources. */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------

    private void performSync() {
        if (supabaseUrl == null || supabaseUrl.isEmpty() ||
                supabaseAnonKey == null || supabaseAnonKey.isEmpty() ||
                supabaseUrl.startsWith("https://YOUR_PROJECT")) {
            Log.w(TAG, "Supabase credentials not configured — skipping sync.");
            return;
        }

        try {
            syncDevices();
            syncObservations();
            syncSessions();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Device sync — upserts all devices (relatively few rows)
    // -------------------------------------------------------------------------

    private void syncDevices() throws IOException, JSONException {
        List<DeviceEntity> devices = dao.getAllDevices();
        if (devices.isEmpty()) return;

        JSONArray body = new JSONArray();
        for (DeviceEntity d : devices) {
            JSONObject obj = new JSONObject();
            obj.put("id",                d.id);
            obj.put("device_identifier", d.deviceIdentifier != null ? d.deviceIdentifier : JSONObject.NULL);
            obj.put("source",            d.source    != null ? d.source    : JSONObject.NULL);
            obj.put("first_seen",        d.firstSeen != null ? d.firstSeen : JSONObject.NULL);
            obj.put("last_seen",         d.lastSeen  != null ? d.lastSeen  : JSONObject.NULL);
            body.put(obj);
        }

        post(supabaseUrl + "/rest/v1/devices?on_conflict=id", body.toString());
        Log.d(TAG, "Synced " + devices.size() + " device(s) to Supabase.");
    }

    // -------------------------------------------------------------------------
    // Observation sync — uploads only rows after the high-water mark
    // -------------------------------------------------------------------------

    private void syncObservations() throws IOException, JSONException {
        long lastSyncedId = prefs.getLong(KEY_LAST_SYNCED_OBS_ID, 0);
        List<ObservationEntity> pending = dao.getObservationsAfter(lastSyncedId, BATCH_SIZE);
        if (pending.isEmpty()) return;

        JSONArray body = new JSONArray();
        long maxId = lastSyncedId;

        for (ObservationEntity obs : pending) {
            JSONObject obj = new JSONObject();
            obj.put("id",        obs.id);
            obj.put("device_id", obs.deviceId);
            obj.put("timestamp", obs.timestamp != null ? obs.timestamp : JSONObject.NULL);
            obj.put("rssi",      obs.rssi);
            obj.put("source",    obs.source != null ? obs.source : JSONObject.NULL);
            body.put(obj);
            if (obs.id > maxId) maxId = obs.id;
        }

        // Only advance the high-water mark AFTER a confirmed successful HTTP call.
        post(supabaseUrl + "/rest/v1/observations?on_conflict=id", body.toString());
        prefs.edit().putLong(KEY_LAST_SYNCED_OBS_ID, maxId).apply();
        Log.d(TAG, "Synced " + pending.size() + " observation(s) (last id=" + maxId + ").");
    }

    // -------------------------------------------------------------------------
    // Session sync — uploads only rows after the high-water mark
    // -------------------------------------------------------------------------

    private void syncSessions() throws IOException, JSONException {
        long lastSyncedId = prefs.getLong(KEY_LAST_SYNCED_SESSION_ID, 0);
        List<SessionEntity> pending = dao.getSessionsAfter(lastSyncedId, BATCH_SIZE);
        if (pending.isEmpty()) return;

        JSONArray body = new JSONArray();
        long maxId = lastSyncedId;

        for (SessionEntity s : pending) {
            JSONObject obj = new JSONObject();
            obj.put("id",         s.id);
            obj.put("device_id",  s.deviceId);
            obj.put("start_time", s.startTime != null ? s.startTime : JSONObject.NULL);
            obj.put("end_time",   s.endTime   != null ? s.endTime   : JSONObject.NULL);
            obj.put("duration",   s.duration);
            body.put(obj);
            if (s.id > maxId) maxId = s.id;
        }

        // Re-upsert sessions so updated duration values are pushed too.
        post(supabaseUrl + "/rest/v1/sessions?on_conflict=id", body.toString());
        prefs.edit().putLong(KEY_LAST_SYNCED_SESSION_ID, maxId).apply();
        Log.d(TAG, "Synced " + pending.size() + " session(s) (last id=" + maxId + ").");
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    private void post(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("apikey", supabaseAnonKey);
            conn.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String errorBody = "";
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) errorBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new IOException("Supabase returned HTTP " + code + ": " + errorBody);
            }
        } finally {
            conn.disconnect();
        }
    }
}
