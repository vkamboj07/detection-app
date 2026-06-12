-- ============================================================
-- Supabase Schema for Billboard Analytics
-- Run these statements in your Supabase SQL editor:
--   https://supabase.com/dashboard → SQL Editor
-- ============================================================

-- Devices table
CREATE TABLE IF NOT EXISTS public.devices (
    id              BIGINT PRIMARY KEY,
    device_identifier TEXT UNIQUE,
    source          TEXT,
    first_seen      TIMESTAMPTZ,
    last_seen       TIMESTAMPTZ
);

-- Observations table
CREATE TABLE IF NOT EXISTS public.observations (
    id              BIGINT PRIMARY KEY,
    device_id       BIGINT REFERENCES public.devices(id) ON DELETE CASCADE,
    timestamp       TIMESTAMPTZ,
    rssi            INTEGER,
    source          TEXT
);

-- Sessions table (dwell-time windows per device)
CREATE TABLE IF NOT EXISTS public.sessions (
    id              BIGINT PRIMARY KEY,
    device_id       BIGINT REFERENCES public.devices(id) ON DELETE CASCADE,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    duration        BIGINT  -- milliseconds
);

-- Indexes for faster dashboard queries
CREATE INDEX IF NOT EXISTS idx_observations_device_id  ON public.observations(device_id);
CREATE INDEX IF NOT EXISTS idx_observations_timestamp  ON public.observations(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sessions_device_id      ON public.sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_sessions_start_time     ON public.sessions(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen       ON public.devices(last_seen DESC);

-- Enable Row-Level Security (recommended — lock down anon access)
ALTER TABLE public.devices      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sessions     ENABLE ROW LEVEL SECURITY;

-- Allow the anon key to INSERT and SELECT (needed by Android app + dashboard)
-- Remove or tighten the INSERT policies once you add proper auth.
CREATE POLICY "anon_select_devices"      ON public.devices      FOR SELECT USING (true);
CREATE POLICY "anon_insert_devices"      ON public.devices      FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_devices"      ON public.devices      FOR UPDATE USING (true);

CREATE POLICY "anon_select_observations" ON public.observations FOR SELECT USING (true);
CREATE POLICY "anon_insert_observations" ON public.observations FOR INSERT WITH CHECK (true);

CREATE POLICY "anon_select_sessions"     ON public.sessions     FOR SELECT USING (true);
CREATE POLICY "anon_insert_sessions"     ON public.sessions     FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_sessions"     ON public.sessions     FOR UPDATE USING (true);

-- Enable real-time replication for the dashboard live feed
ALTER PUBLICATION supabase_realtime ADD TABLE public.devices;
ALTER PUBLICATION supabase_realtime ADD TABLE public.observations;
ALTER PUBLICATION supabase_realtime ADD TABLE public.sessions;
