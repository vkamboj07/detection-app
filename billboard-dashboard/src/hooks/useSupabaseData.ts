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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(supabaseConfigError);
  const [sessionKey, setSessionKey] = useState(0);

  const resetSession = () => {
    setDevices([]);
    setObservations([]);
    setSessions([]);
    setLoading(false);
    setError(null);
    setSessionKey(k => k + 1);
  };

  useEffect(() => {
    // Don't attempt any network calls if credentials are missing
    if (supabaseConfigError) {
      setLoading(false);
      return;
    }

    let mounted = true;

    async function fetchInitialData() {
      try {
        setLoading(true);

        // Fetch last 24 hours of data for initial load
        const oneDayAgo = new Date();
        oneDayAgo.setDate(oneDayAgo.getDate() - 1);

        const [devicesRes, obsRes, sessionsRes] = await Promise.all([
          supabase.from('devices').select('*'),
          supabase
            .from('observations')
            .select('*')
            .gte('timestamp', oneDayAgo.toISOString())
            .order('timestamp', { ascending: false })
            .limit(5000),
          supabase
            .from('sessions')
            .select('*')
            .gte('start_time', oneDayAgo.toISOString())
            .order('start_time', { ascending: false })
            .limit(2000),
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

  return { devices, observations, sessions, loading, error, resetSession };
}
