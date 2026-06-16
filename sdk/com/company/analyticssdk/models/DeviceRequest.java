package com.company.analyticssdk.models;

import com.google.gson.annotations.SerializedName;

/**
 * Request body for the POST /register-device endpoint.
 */
public class DeviceRequest {

    @SerializedName("device_id")
    private final String deviceId;

    public DeviceRequest(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
