-- ============================================================
-- Supabase Schema for Billboard Analytics
-- Run these statements in your Supabase SQL editor:
--   https://supabase.com/dashboard → SQL Editor
-- ============================================================

-- Devices table
CREATE TABLE IF NOT EXISTS public.devices (
    id              BIGSERIAL PRIMARY KEY,
    device_identifier TEXT NOT NULL,
    source          TEXT NOT NULL,
    first_seen      TIMESTAMPTZ DEFAULT NOW(),
    last_seen       TIMESTAMPTZ DEFAULT NOW()
);

-- Observations table
CREATE TABLE IF NOT EXISTS public.observations (
    id              BIGSERIAL PRIMARY KEY,
    device_id       BIGINT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    timestamp       TIMESTAMPTZ DEFAULT NOW(),
    rssi            INTEGER NOT NULL,
    source          TEXT NOT NULL
);

-- Sessions table (dwell-time windows per device)
CREATE TABLE IF NOT EXISTS public.sessions (
    id              BIGSERIAL PRIMARY KEY,
    device_id       BIGINT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    start_time      TIMESTAMPTZ DEFAULT NOW(),
    end_time        TIMESTAMPTZ DEFAULT NOW(),
    duration        BIGINT NOT NULL DEFAULT 0
);

-- Indexes for faster dashboard queries
CREATE INDEX IF NOT EXISTS idx_observations_device_id  ON public.observations(device_id);
CREATE INDEX IF NOT EXISTS idx_observations_timestamp  ON public.observations(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_sessions_device_id      ON public.sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_sessions_start_time     ON public.sessions(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen       ON public.devices(last_seen DESC);
CREATE INDEX IF NOT EXISTS idx_devices_identifier      ON public.devices(device_identifier);

-- Enable Row-Level Security (recommended — lock down anon access)
ALTER TABLE public.devices      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sessions     ENABLE ROW LEVEL SECURITY;

-- Allow the anon key to INSERT, SELECT, UPDATE, and DELETE (needed by Android app + dashboard)
-- WARNING: These policies grant full read/write access to anyone with the anon key.
-- For production, replace with proper auth-based policies (e.g. USING (auth.role() = 'authenticated'))
-- and remove any DELETE policies you don't want public clients to have.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_select_devices') THEN
    CREATE POLICY "anon_select_devices" ON public.devices FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_insert_devices') THEN
    CREATE POLICY "anon_insert_devices" ON public.devices FOR INSERT WITH CHECK (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_update_devices') THEN
    CREATE POLICY "anon_update_devices" ON public.devices FOR UPDATE USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_select_observations') THEN
    CREATE POLICY "anon_select_observations" ON public.observations FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_insert_observations') THEN
    CREATE POLICY "anon_insert_observations" ON public.observations FOR INSERT WITH CHECK (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_select_sessions') THEN
    CREATE POLICY "anon_select_sessions" ON public.sessions FOR SELECT USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_insert_sessions') THEN
    CREATE POLICY "anon_insert_sessions" ON public.sessions FOR INSERT WITH CHECK (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_update_sessions') THEN
    CREATE POLICY "anon_update_sessions" ON public.sessions FOR UPDATE USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_delete_devices') THEN
    CREATE POLICY "anon_delete_devices" ON public.devices FOR DELETE USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_delete_observations') THEN
    CREATE POLICY "anon_delete_observations" ON public.observations FOR DELETE USING (true);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'anon_delete_sessions') THEN
    CREATE POLICY "anon_delete_sessions" ON public.sessions FOR DELETE USING (true);
  END IF;
END $$;

-- Enable real-time replication for the dashboard live feed
ALTER PUBLICATION supabase_realtime ADD TABLE IF NOT EXISTS public.devices;
ALTER PUBLICATION supabase_realtime ADD TABLE IF NOT EXISTS public.observations;
ALTER PUBLICATION supabase_realtime ADD TABLE IF NOT EXISTS public.sessions;
