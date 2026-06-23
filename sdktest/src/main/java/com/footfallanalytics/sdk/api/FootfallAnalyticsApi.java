package com.footfallanalytics.sdk.api;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class FootfallAnalyticsApi {

    private static final String TAG = "FSDK_Api";

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final ScheduledExecutorService executor;

    public FootfallAnalyticsApi(String supabaseUrl, String supabaseAnonKey) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void getUniqueDeviceCount24h(final ApiCallback<Integer> callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String base = supabaseUrl.replaceAll("/+$", "");
                URL url = new URL(base + "/functions/v1/unique-devices");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("apikey", supabaseAnonKey);
                conn.setRequestProperty("Authorization", "Bearer " + supabaseAnonKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    String errBody = readBody(conn.getErrorStream());
                    throw new IOException("API returned HTTP " + code + ": " + errBody);
                }

                String body = readBody(conn.getInputStream());
                JSONObject json = new JSONObject(body);
                int count = json.getInt("count");

                if (callback != null) {
                    callback.onSuccess(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "getUniqueDeviceCount24h failed", e);
                if (callback != null) {
                    callback.onError(e);
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }
}
