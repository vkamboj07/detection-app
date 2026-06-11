CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    device_identifier TEXT NOT NULL,
    source TEXT NOT NULL,
    first_seen TIMESTAMPTZ DEFAULT NOW(),
    last_seen TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE observations (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    rssi INTEGER NOT NULL,
    source TEXT NOT NULL
);

CREATE INDEX idx_observations_timestamp ON observations(timestamp DESC);
CREATE INDEX idx_devices_identifier ON devices(device_identifier);
