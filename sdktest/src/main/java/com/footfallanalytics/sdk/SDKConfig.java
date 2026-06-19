package com.footfallanalytics.sdk;

public class SDKConfig {
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final long sessionTimeoutMs;
    private final long wifiPollIntervalMs;
    private final boolean enableClassicBtScanning;

    private SDKConfig(Builder builder) {
        this.supabaseUrl = builder.supabaseUrl;
        this.supabaseAnonKey = builder.supabaseAnonKey;
        this.sessionTimeoutMs = builder.sessionTimeoutMs;
        this.wifiPollIntervalMs = builder.wifiPollIntervalMs;
        this.enableClassicBtScanning = builder.enableClassicBtScanning;
    }

    public String getSupabaseUrl() { return supabaseUrl; }
    public String getSupabaseAnonKey() { return supabaseAnonKey; }
    public long getSessionTimeoutMs() { return sessionTimeoutMs; }
    public long getWifiPollIntervalMs() { return wifiPollIntervalMs; }
    public boolean isClassicBtScanningEnabled() { return enableClassicBtScanning; }

    public boolean hasCloudSyncConfig() {
        return supabaseUrl != null && !supabaseUrl.isEmpty()
                && supabaseAnonKey != null && !supabaseAnonKey.isEmpty();
    }

    public static class Builder {
        private String supabaseUrl = "";
        private String supabaseAnonKey = "";
        private long sessionTimeoutMs = 10 * 60 * 1000;
        private long wifiPollIntervalMs = 15_000;
        private boolean enableClassicBtScanning = false;

        public Builder setSupabaseUrl(String url) {
            this.supabaseUrl = url;
            return this;
        }

        public Builder setSupabaseAnonKey(String key) {
            this.supabaseAnonKey = key;
            return this;
        }

        public Builder setSessionTimeoutMs(long timeoutMs) {
            this.sessionTimeoutMs = timeoutMs;
            return this;
        }

        public Builder setWifiPollIntervalMs(long intervalMs) {
            this.wifiPollIntervalMs = intervalMs;
            return this;
        }

        public Builder setClassicBtScanningEnabled(boolean enabled) {
            this.enableClassicBtScanning = enabled;
            return this;
        }

        public SDKConfig build() {
            return new SDKConfig(this);
        }
    }
}
