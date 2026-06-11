import { useState, useEffect } from 'react';
import { supabase } from '../lib/supabase';
import type { Database } from '../types/supabase';

type Device = Database['public']['Tables']['devices']['Row'];
type Observation = Database['public']['Tables']['observations']['Row'];

export function useSupabaseData() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [observations, setObservations] = useState<Observation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function fetchInitialData() {
      try {
        setLoading(true);
        
        // Fetch last 24 hours of observations for initial load
        const oneDayAgo = new Date();
        oneDayAgo.setDate(oneDayAgo.getDate() - 1);
        
        const [devicesRes, obsRes] = await Promise.all([
          supabase.from('devices').select('*'),
          supabase
            .from('observations')
            .select('*')
            .gte('timestamp', oneDayAgo.toISOString())
            .order('timestamp', { ascending: false })
            .limit(5000)
        ]);

        if (devicesRes.error) throw devicesRes.error;
        if (obsRes.error) throw obsRes.error;

        if (mounted) {
          setDevices(devicesRes.data || []);
          setObservations(obsRes.data || []);
          setLoading(false);
        }
      } catch (err) {
        console.error('Error fetching initial data:', err);
        const errorMessage = err instanceof Error ? err.message : String(err);
        if (mounted) {
          setError(errorMessage);
          setLoading(false); // must clear loading on error or UI is permanently stuck
        }
      }
    }

    fetchInitialData();

    // Subscribe to real-time updates for observations
    const obsSubscription = supabase
      .channel('public:observations')
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'observations' },
        (payload) => {
          const newObs = payload.new as Observation;
          setObservations((current) => [newObs, ...current].slice(0, 5000)); // Keep latest 5k
        }
      )
      .subscribe();

    // Subscribe to real-time updates for devices
    const deviceSubscription = supabase
      .channel('public:devices')
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

    return () => {
      mounted = false;
      supabase.removeChannel(obsSubscription);
      supabase.removeChannel(deviceSubscription);
    };
  }, []);

  return { devices, observations, loading, error };
}
