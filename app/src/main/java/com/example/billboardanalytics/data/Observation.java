package com.example.billboardanalytics.data;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class Observation {
    @SerializedName("source")
    private String source; // "BLE", "BT_CLASSIC", "WIFI"
    
    @SerializedName("mac")
    private String mac;
    
    @SerializedName("rssi")
    private int rssi;
    
    @SerializedName("timestamp")
    private String timestamp; // ISO-8601 string
    
    // Optional fields depending on the scanner
    @SerializedName("deviceName")
    private String deviceName;
    
    @SerializedName("deviceType")
    private String deviceType;
    
    @SerializedName("advertisementData")
    private String advertisementData;
    
    @SerializedName("manufacturerData")
    private String manufacturerData;
    
    // Wi-Fi specific fields
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

    // Getters and Setters
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

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getAdvertisementData() { return advertisementData; }
    public void setAdvertisementData(String advertisementData) { this.advertisementData = advertisementData; }

    public String getManufacturerData() { return manufacturerData; }
    public void setManufacturerData(String manufacturerData) { this.manufacturerData = manufacturerData; }

    public String getBssid() { return bssid; }
    public void setBssid(String bssid) { this.bssid = bssid; }

    public String getSsid() { return ssid; }
    public void setSsid(String ssid) { this.ssid = ssid; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
