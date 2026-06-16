package com.company.analyticssdk;

import android.util.Log;

import com.company.analyticssdk.models.DeviceRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Wraps Retrofit and exposes a single method for device registration.
 * All network calls are asynchronous (Retrofit's enqueue).
 */
class NetworkManager {

    private static final String TAG = "AnalyticsSDK";

    private final ApiService apiService;

    NetworkManager(String baseUrl) {
        // Ensure the base URL always ends with '/' as required by Retrofit
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    /**
     * Sends an async POST /register-device request.
     *
     * @param deviceId the device identifier to register
     * @param callback called on success or failure
     */
    void registerDevice(String deviceId, RegistrationCallback callback) {
        DeviceRequest request = new DeviceRequest(deviceId);

        apiService.registerDevice(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Registration successful");
                    callback.onSuccess();
                } else {
                    Log.e(TAG, "Registration failed: HTTP " + response.code());
                    callback.onFailure(new Exception("HTTP " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "Registration failed: " + t.getMessage());
                callback.onFailure(new Exception(t));
            }
        });
    }

    /** Callback interface for device registration results. */
    interface RegistrationCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}
