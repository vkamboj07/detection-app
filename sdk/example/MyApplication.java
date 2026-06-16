package com.example.myapp;

import android.app.Application;

import com.company.analyticssdk.AnalyticsSDK;

/**
 * Example integration — call initialize() once in Application.onCreate()
 * so it runs before any Activity or Service starts.
 */
public class MyApplication extends Application {

    // Replace with your actual backend base URL
    private static final String ANALYTICS_API_URL = "https://api.example.com/";

    @Override
    public void onCreate() {
        super.onCreate();

        AnalyticsSDK.getInstance().initialize(this, ANALYTICS_API_URL);
    }
}
