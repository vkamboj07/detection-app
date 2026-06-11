import React, { createContext, useContext, useMemo } from 'react';
import { useSupabaseData } from '../hooks/useSupabaseData';
import type { Database } from '../types/supabase';

type Device = Database['public']['Tables']['devices']['Row'];
type Observation = Database['public']['Tables']['observations']['Row'];

interface AnalyticsContextType {
  devices: Device[];
  observations: Observation[];
  loading: boolean;
  error: string | null;
  activeDevices: Device[];
  totalDevices: number;
  newDevicesToday: number;
  totalObservations: number;
}

const AnalyticsContext = createContext<AnalyticsContextType | undefined>(undefined);

export function AnalyticsProvider({ children }: { children: React.ReactNode }) {
  const { devices, observations, loading, error } = useSupabaseData();

  const metrics = useMemo(() => {
    const now = new Date();
    const fiveMinsAgo = new Date(now.getTime() - 5 * 60 * 1000);
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());

    const activeDeviceIds = new Set(
      observations
        .filter(o => new Date(o.timestamp) >= fiveMinsAgo)
        .map(o => o.device_id)
    );

    const activeDevices = devices.filter(d => activeDeviceIds.has(d.id));

    const newDevicesToday = devices.filter(
      d => d.first_seen && new Date(d.first_seen) >= startOfToday
    ).length;

    return {
      activeDevices,
      totalDevices: devices.length,
      newDevicesToday,
      totalObservations: observations.length,
    };
  }, [devices, observations]);

  const value = {
    devices,
    observations,
    loading,
    error,
    ...metrics,
  };

  return (
    <AnalyticsContext.Provider value={value}>
      {children}
    </AnalyticsContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAnalytics() {
  const context = useContext(AnalyticsContext);
  if (context === undefined) {
    throw new Error('useAnalytics must be used within an AnalyticsProvider');
  }
  return context;
}
