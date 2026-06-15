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

CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ DEFAULT NOW(),
    end_time TIMESTAMPTZ DEFAULT NOW(),
    duration BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_observations_timestamp ON observations(timestamp DESC);
CREATE INDEX idx_observations_device_id ON observations(device_id);
CREATE INDEX idx_sessions_device_id ON sessions(device_id);
CREATE INDEX idx_sessions_start_time ON sessions(start_time DESC);
CREATE INDEX idx_devices_identifier ON devices(device_identifier);
CREATE INDEX idx_devices_last_seen ON devices(last_seen DESC);

-- Enable Row-Level Security
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;

-- Allow anon key access (needed by Android app + dashboard)
CREATE POLICY "anon_select_devices"      ON devices      FOR SELECT USING (true);
CREATE POLICY "anon_insert_devices"      ON devices      FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_devices"      ON devices      FOR UPDATE USING (true);

CREATE POLICY "anon_select_observations" ON observations FOR SELECT USING (true);
CREATE POLICY "anon_insert_observations" ON observations FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_sessions"     ON sessions     FOR SELECT USING (true);
CREATE POLICY "anon_insert_sessions"     ON sessions     FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_sessions"     ON sessions     FOR UPDATE USING (true);

-- Enable real-time replication for the dashboard live feed
ALTER PUBLICATION supabase_realtime ADD TABLE devices;
ALTER PUBLICATION supabase_realtime ADD TABLE observations;
ALTER PUBLICATION supabase_realtime ADD TABLE sessions;
