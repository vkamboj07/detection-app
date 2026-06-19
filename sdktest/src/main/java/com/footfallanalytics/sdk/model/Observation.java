package com.footfallanalytics.sdk.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class Observation {
    private static final Gson GSON = new Gson();
    @SerializedName("source")
    private String source;

    @SerializedName("mac")
    private String mac;

    @SerializedName("rssi")
    private int rssi;

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("deviceName")
    private String deviceName;

    @SerializedName("manufacturerData")
    private String manufacturerData;

    @SerializedName("bssid")
    private String bssid;

    @SerializedName("ssid")
    private String ssid;

    @SerializedName("frequency")
    private int frequency;

    public Observation(String source, String mac, int rssi, String timestamp) {
        this.source = source;
        this.mac = mac;
        this.rssi = rssi;
        this.timestamp = timestamp;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMac() { return mac; }
    public void setMac(String mac) { this.mac = mac; }

    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getManufacturerData() { return manufacturerData; }
    public void setManufacturerData(String manufacturerData) { this.manufacturerData = manufacturerData; }

    public String getBssid() { return bssid; }
    public void setBssid(String bssid) { this.bssid = bssid; }

    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public String toJson() {
        return GSON.toJson(this);
    }
}
