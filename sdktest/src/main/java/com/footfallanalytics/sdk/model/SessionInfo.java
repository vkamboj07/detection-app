package com.footfallanalytics.sdk.model;

public class SessionInfo {
    private long id;
    private long deviceId;
    private String startTime;
    private String endTime;
    private long duration;

    public SessionInfo() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDeviceId() { return deviceId; }
    public void setDeviceId(long deviceId) { this.deviceId = deviceId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}
