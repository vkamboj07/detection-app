package com.example.billboardanalytics.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.billboardanalytics.data.AnalyticsDao;
import com.example.billboardanalytics.data.DeviceEntity;
import com.example.billboardanalytics.data.ObservationEntity;

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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Syncs locally-stored Room data (devices + observations) to the Supabase REST API
 * so the web dashboard receives live data from the Android scanner.
 *
 * Sync is debounced — multiple rapid calls to {@link #syncAsync()} within
 * {@code DEBOUNCE_SECONDS} are collapsed into a single upload, so BLE scans that fire
 * every few seconds don't flood the network.
 *
 * Sync is best-effort: failures are logged but never crash the app.  The high-water
 * mark is only advanced after a confirmed successful HTTP response so no rows are lost.
 */
public class SupabaseSyncManager {

    private static final String TAG = "SupabaseSyncManager";
    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_LAST_SYNCED_OBS_ID = "last_synced_obs_id";
    private static final int BATCH_SIZE = 50;
    private static final long DEBOUNCE_SECONDS = 10; // collapse rapid calls into one sync every 10s

    private final AnalyticsDao dao;
    private final SharedPreferences prefs;
    private final String supabaseUrl;
    private final String supabaseAnonKey;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingSync;

    public SupabaseSyncManager(Context context, AnalyticsDao dao,
                               String supabaseUrl, String supabaseAnonKey) {
        this.dao = dao;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    /**
     * Schedules a sync. Multiple calls within {@code DEBOUNCE_SECONDS} are collapsed
     * into a single upload — safe to call on every BLE detection.
     */
    public synchronized void syncAsync() {
        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false); // reset the debounce timer
        }
        pendingSync = scheduler.schedule(this::performSync, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
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
            obj.put("id", d.id);
            obj.put("device_identifier", d.deviceIdentifier != null ? d.deviceIdentifier : JSONObject.NULL);
            obj.put("source", d.source != null ? d.source : JSONObject.NULL);
            obj.put("first_seen", d.firstSeen != null ? d.firstSeen : JSONObject.NULL);
            obj.put("last_seen", d.lastSeen != null ? d.lastSeen : JSONObject.NULL);
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
            obj.put("id", obs.id);
            obj.put("device_id", obs.deviceId);
            obj.put("timestamp", obs.timestamp != null ? obs.timestamp : JSONObject.NULL);
            obj.put("rssi", obs.rssi);
            obj.put("source", obs.source != null ? obs.source : JSONObject.NULL);
            body.put(obj);
            if (obs.id > maxId) maxId = obs.id;
        }

        // Only advance the high-water mark AFTER a confirmed successful HTTP call.
        // If post() throws, the mark stays at the old value and rows are retried next sync.
        post(supabaseUrl + "/rest/v1/observations?on_conflict=id", body.toString());
        prefs.edit().putLong(KEY_LAST_SYNCED_OBS_ID, maxId).apply();
        Log.d(TAG, "Synced " + pending.size() + " observation(s) (last id=" + maxId + ").");
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
            // merge-duplicates = upsert on conflict
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
                // Read error body for better diagnostics
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
