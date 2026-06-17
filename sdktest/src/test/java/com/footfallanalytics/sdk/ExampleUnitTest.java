package com.footfallanalytics.sdk;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void sdkConfig_buildsWithDefaults() {
        SDKConfig config = new SDKConfig.Builder().build();
        assertNotNull(config);
        assertEquals(10 * 60 * 1000, config.getSessionTimeoutMs());
        assertEquals(15000, config.getWifiPollIntervalMs());
        assertTrue(config.isClassicBtScanningEnabled());
        assertFalse(config.hasCloudSyncConfig());
    }

    @Test
    public void sdkConfig_customValues() {
        SDKConfig config = new SDKConfig.Builder()
                .setSupabaseUrl("https://test.supabase.co")
                .setSupabaseAnonKey("test-key")
                .setSessionTimeoutMs(300000)
                .setWifiPollIntervalMs(30000)
                .setClassicBtScanningEnabled(false)
                .build();
        assertEquals("https://test.supabase.co", config.getSupabaseUrl());
        assertEquals("test-key", config.getSupabaseAnonKey());
        assertTrue(config.hasCloudSyncConfig());
        assertEquals(300000, config.getSessionTimeoutMs());
        assertEquals(30000, config.getWifiPollIntervalMs());
        assertFalse(config.isClassicBtScanningEnabled());
    }
}
