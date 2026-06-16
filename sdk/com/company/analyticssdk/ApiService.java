package com.company.analyticssdk;

import com.company.analyticssdk.models.DeviceRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Retrofit interface describing the analytics backend endpoints.
 */
public interface ApiService {

    /**
     * Registers a unique device with the backend.
     *
     * @param request body containing the device_id
     * @return a Retrofit {@link Call} with a Void response body
     */
    @POST("register-device")
    Call<Void> registerDevice(@Body DeviceRequest request);
}
