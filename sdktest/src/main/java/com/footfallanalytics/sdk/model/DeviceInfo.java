package com.footfallanalytics.sdk.model;

public class DeviceInfo {
    private long id;
    private String deviceIdentifier;
    private String source;
    private String firstSeen;
    private String lastSeen;

    public DeviceInfo() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getDeviceIdentifier() { return deviceIdentifier; }
    public void setDeviceIdentifier(String deviceIdentifier) { this.deviceIdentifier = deviceIdentifier; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getFirstSeen() { return firstSeen; }
    public void setFirstSeen(String firstSeen) { this.firstSeen = firstSeen; }

    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
}
