package com.footfallanalytics.sdk.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.RestrictTo;

import com.footfallanalytics.sdk.data.AnalyticsDao;
import com.footfallanalytics.sdk.data.DeviceEntity;
import com.footfallanalytics.sdk.data.ObservationEntity;
import com.footfallanalytics.sdk.data.SessionEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SupabaseSyncManager {

    private static final String TAG = "FSDK_SyncManager";
    private static final String PREFS_NAME = "fsdk_sync_prefs";
    private static final String KEY_LAST_SYNCED_OBS_ID     = "last_synced_obs_id";
    private static final String KEY_LAST_SYNCED_SESSION_ID = "last_synced_session_id";
    private static final String KEY_DIRTY_SESSION_IDS      = "dirty_session_ids";
    private static final int    BATCH_SIZE       = 50;
    private static final long   DEBOUNCE_SECONDS = 10;
    private static final long   MAX_WAIT_SECONDS = 30;

    private final AnalyticsDao      dao;
    private final SharedPreferences prefs;
    private final String            supabaseUrl;
    private final String            supabaseAnonKey;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingSync;
    private long burstStartMs = 0;

    public SupabaseSyncManager(Context context, AnalyticsDao dao,
                                String supabaseUrl, String supabaseAnonKey) {
        this.dao            = dao;
        this.prefs          = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.supabaseUrl    = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    public synchronized void syncAsync() {
        if (isNotConfigured()) return;
        long now = System.currentTimeMillis();
        if (pendingSync == null || pendingSync.isDone()) {
            burstStartMs = now;
        }
        long msSinceBurstStart = now - burstStartMs;
        long remainingCeilingMs = (MAX_WAIT_SECONDS * 1000) - msSinceBurstStart;
        long delayMs = Math.min(DEBOUNCE_SECONDS * 1000, Math.max(0, remainingCeilingMs));

        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }
        try {
            pendingSync = scheduler.schedule(this::performSync, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "syncAsync rejected - scheduler is shut down");
        }
    }

    public synchronized void syncImmediately() {
        if (isNotConfigured()) return;
        if (pendingSync != null && !pendingSync.isDone()) {
            pendingSync.cancel(false);
        }
        burstStartMs = 0;
        try {
            scheduler.execute(this::performSync);
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "syncImmediately rejected - scheduler is shut down");
        }
    }

    public synchronized void markSessionDirty(long sessionId) {
        Set<String> dirty = new HashSet<>(prefs.getStringSet(KEY_DIRTY_SESSION_IDS, Collections.emptySet()));
        dirty.add(String.valueOf(sessionId));
        prefs.edit().putStringSet(KEY_DIRTY_SESSION_IDS, dirty).apply();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private boolean isNotConfigured() {
        return supabaseUrl == null || supabaseUrl.isEmpty()
                || supabaseAnonKey == null || supabaseAnonKey.isEmpty();
    }

    private void performSync() {
        try {
            syncDevices();
            syncObservations();
            syncSessions();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed: " + e.getMessage(), e);
        }
    }

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
    }

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
        post(supabaseUrl + "/rest/v1/observations?on_conflict=id", body.toString());
        prefs.edit().putLong(KEY_LAST_SYNCED_OBS_ID, maxId).apply();
    }

    private void syncSessions() throws IOException, JSONException {
        long lastSyncedId = prefs.getLong(KEY_LAST_SYNCED_SESSION_ID, 0);
        Set<String> dirtySet = new HashSet<>(prefs.getStringSet(KEY_DIRTY_SESSION_IDS, Collections.emptySet()));
        boolean hasDirty = !dirtySet.isEmpty();

        List<SessionEntity> allSessions = dao.getAllSessions();
        if (allSessions.isEmpty()) return;

        JSONArray body = new JSONArray();
        long maxId = lastSyncedId;
        for (SessionEntity s : allSessions) {
            if (s.id > lastSyncedId || (hasDirty && dirtySet.contains(String.valueOf(s.id)))) {
                JSONObject obj = new JSONObject();
                obj.put("id",         s.id);
                obj.put("device_id",  s.deviceId);
                obj.put("start_time", s.startTime != null ? s.startTime : JSONObject.NULL);
                obj.put("end_time",   s.endTime   != null ? s.endTime   : JSONObject.NULL);
                obj.put("duration",   s.duration);
                body.put(obj);
                if (s.id > maxId) maxId = s.id;
            }
        }
        if (body.length() == 0) return;

        post(supabaseUrl + "/rest/v1/sessions?on_conflict=id", body.toString());
        prefs.edit()
            .putLong(KEY_LAST_SYNCED_SESSION_ID, maxId)
            .putStringSet(KEY_DIRTY_SESSION_IDS, Collections.emptySet())
            .apply();
    }

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
                String errorBody = readErrorBody(conn);
                throw new IOException("Supabase returned HTTP " + code + ": " + errorBody);
            }
        } finally {
            conn.disconnect();
        }
    }

    private String readErrorBody(HttpURLConnection conn) {
        try (InputStream es = conn.getErrorStream()) {
            if (es == null) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(es, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "(could not read error body)";
        }
    }
}
