import { useState, useEffect } from 'react';
import { supabase, supabaseConfigError } from '../lib/supabase';
import type { Database } from '../types/supabase';

type Device = Database['public']['Tables']['devices']['Row'];
type Observation = Database['public']['Tables']['observations']['Row'];
type Session = Database['public']['Tables']['sessions']['Row'];

export function useSupabaseData() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [observations, setObservations] = useState<Observation[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [loading, setLoading] = useState(!supabaseConfigError);
  const [error, setError] = useState<string | null>(supabaseConfigError);
  const [sessionKey, setSessionKey] = useState(0);
  const [resetting, setResetting] = useState(false);

  /**
   * Drops all rows from devices, observations, and sessions tables in Supabase,
   * then resets local state and re-subscribes to live data.
   *
   * Order matters: delete child rows (observations, sessions) before the parent
   * (devices) to avoid FK constraint violations if cascades are not configured.
   */
  const resetSession = async () => {
    if (supabaseConfigError) return;
    setResetting(true);
    try {
      // Step 1: delete child tables in parallel (both reference devices)
      const [obsDelete, sessDelete] = await Promise.all([
        supabase.from('observations').delete().gte('id', 0),
        supabase.from('sessions').delete().gte('id', 0),
      ]);

      if (obsDelete.error) throw obsDelete.error;
      if (sessDelete.error) {
        console.warn('Sessions delete error (may not exist yet):', sessDelete.error.message);
      }

      // Step 2: delete parent table after children are gone
      const devDelete = await supabase.from('devices').delete().gte('id', 0);
      if (devDelete.error) throw devDelete.error;

    } catch (err) {
      console.error('Error resetting session data:', err);
      const msg = err instanceof Error ? err.message : String(err);
      setError(`Reset failed: ${msg}`);
      setResetting(false);
      return;
    }

    // Clear local state and trigger a fresh re-subscribe via sessionKey bump
    setDevices([]);
    setObservations([]);
    setSessions([]);
    setLoading(false);
    setError(null);
    setResetting(false);
    setSessionKey(k => k + 1);
  };

  useEffect(() => {
    // Don't attempt any network calls if credentials are missing
    if (supabaseConfigError) {
      return;
    }

    let mounted = true;

    async function fetchInitialData() {
      try {
        setLoading(true);

        // Fetch today's data (UTC calendar day) to match the Android app's
        // AnalyticsEngine.generateMetricsForToday() time window.
        const now = new Date();
        const startOfToday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

        const [devicesRes, obsRes, sessionsRes] = await Promise.all([
          supabase.from('devices').select('*'),
          supabase
            .from('observations')
            .select('*')
            .gte('timestamp', startOfToday.toISOString())
            .order('timestamp', { ascending: false })
            .limit(10000),
          supabase
            .from('sessions')
            .select('*')
            .gte('start_time', startOfToday.toISOString())
            .order('start_time', { ascending: false })
            .limit(10000),
        ]);

        if (devicesRes.error) throw devicesRes.error;
        if (obsRes.error) throw obsRes.error;
        // Sessions table may not exist yet — treat as non-fatal
        if (sessionsRes.error) {
          console.warn('Sessions table error (may not be created yet):', sessionsRes.error.message);
        }

        if (mounted) {
          setDevices(devicesRes.data ?? []);
          setObservations(obsRes.data ?? []);
          setSessions(sessionsRes.data ?? []);
          setLoading(false);
        }
      } catch (err) {
        console.error('Error fetching initial data:', err);
        const errorMessage = err instanceof Error ? err.message : String(err);
        if (mounted) {
          setError(errorMessage);
          setLoading(false);
        }
      }
    }

    fetchInitialData();

    // Real-time: observations
    const obsSubscription = supabase
      .channel(`observations-${sessionKey}`)
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'observations' },
        (payload) => {
          const newObs = payload.new as Observation;
          setObservations((current) => [newObs, ...current].slice(0, 5000));
        }
      )
      .subscribe();

    // Real-time: devices (insert + update)
    const deviceSubscription = supabase
      .channel(`devices-${sessionKey}`)
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'devices' },
        (payload) => {
          const newDevice = payload.new as Device;
          setDevices((current) => [...current, newDevice]);
        }
      )
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'devices' },
        (payload) => {
          const updatedDevice = payload.new as Device;
          setDevices((current) =>
            current.map((d) => (d.id === updatedDevice.id ? updatedDevice : d))
          );
        }
      )
      .subscribe();

    // Real-time: sessions (insert + update)
    const sessionSubscription = supabase
      .channel(`sessions-${sessionKey}`)
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'sessions' },
        (payload) => {
          const newSession = payload.new as Session;
          setSessions((current) => [newSession, ...current].slice(0, 2000));
        }
      )
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'sessions' },
        (payload) => {
          const updatedSession = payload.new as Session;
          setSessions((current) =>
            current.map((s) => (s.id === updatedSession.id ? updatedSession : s))
          );
        }
      )
      .subscribe();

    return () => {
      mounted = false;
      supabase.removeChannel(obsSubscription);
      supabase.removeChannel(deviceSubscription);
      supabase.removeChannel(sessionSubscription);
    };
  }, [sessionKey]);

  return { devices, observations, sessions, loading, error, resetting, resetSession };
}
