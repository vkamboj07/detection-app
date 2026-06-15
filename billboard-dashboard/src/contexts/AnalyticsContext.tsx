import React, { createContext, useContext, useMemo } from 'react';
import { useSupabaseData } from '../hooks/useSupabaseData';
import type { Database } from '../types/supabase';

type Device = Database['public']['Tables']['devices']['Row'];
type Observation = Database['public']['Tables']['observations']['Row'];
type Session = Database['public']['Tables']['sessions']['Row'];

interface AnalyticsContextType {
  devices: Device[];
  observations: Observation[];
  sessions: Session[];
  loading: boolean;
  error: string | null;
  resetting: boolean;
  // Computed metrics
  activeDevices: Device[];
  totalDevices: number;
  newDevicesToday: number;
  totalObservations: number;
  averageDwellTimeMs: number;
  peakHour: string;
  returningVisitors: number;
  resetSession: () => Promise<void>;
}

const AnalyticsContext = createContext<AnalyticsContextType | undefined>(undefined);

export function AnalyticsProvider({ children }: { children: React.ReactNode }) {
  const { devices, observations, sessions, loading, error, resetting, resetSession } = useSupabaseData();

  const metrics = useMemo(() => {
    const now = new Date();
    const fiveMinsAgo = new Date(now.getTime() - 5 * 60 * 1000);
    // Use UTC midnight so comparisons match the UTC timestamps stored by the Android app.
    // Using local midnight would shift "today" by the browser's UTC offset, misclassifying
    // devices seen near midnight.
    const startOfToday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

    // Active devices: seen in last 5 minutes
    const activeDeviceIds = new Set(
      observations
        .filter(o => new Date(o.timestamp) >= fiveMinsAgo)
        .map(o => o.device_id)
    );
    const activeDevices = devices.filter(d => activeDeviceIds.has(d.id));

    // New devices today
    const newDevicesToday = devices.filter(
      d => d.first_seen && new Date(d.first_seen) >= startOfToday
    ).length;

    // Returning visitors: devices first seen BEFORE today but seen again today
    const todayFirstSeenIds = new Set(
      devices
        .filter(d => d.first_seen && new Date(d.first_seen) >= startOfToday)
        .map(d => d.id)
    );
    const todayActiveIds = new Set(
      observations
        .filter(o => new Date(o.timestamp) >= startOfToday)
        .map(o => o.device_id)
    );
    const returningVisitors = [...todayActiveIds].filter(id => !todayFirstSeenIds.has(id)).length;

    // Average dwell time from sessions today (duration is stored in ms)
    const todaySessions = sessions.filter(
      s => s.start_time && new Date(s.start_time) >= startOfToday
    );
    const averageDwellTimeMs =
      todaySessions.length > 0
        ? todaySessions.reduce((sum, s) => sum + (s.duration ?? 0), 0) / todaySessions.length
        : 0;

    // Peak hour from sessions today
    const hourCounts: Record<number, number> = {};
    for (let i = 0; i < 24; i++) hourCounts[i] = 0;
    todaySessions.forEach(s => {
      // Use UTC hours to match the UTC timestamps stored by the Android app.
      // getHours() returns local browser time, which shifts the peak hour by the UTC offset.
      const h = new Date(s.start_time).getUTCHours();
      hourCounts[h] = (hourCounts[h] ?? 0) + 1;
    });
    let peakHourNum = 0;
    let peakCount = 0;
    for (const [h, count] of Object.entries(hourCounts)) {
      if (count > peakCount) {
        peakCount = count;
        peakHourNum = Number(h);
      }
    }
    const peakHour =
      todaySessions.length > 0
        ? `${String(peakHourNum).padStart(2, '0')}:00 – ${String((peakHourNum + 1) % 24).padStart(2, '0')}:00`
        : 'N/A';

    // Total visitors today: unique device IDs in sessions that started today
    // (matches Android app's totalVisitorsToday in AnalyticsEngine.java).
    const todaySessionDeviceIds = new Set(todaySessions.map(s => s.device_id));
    const totalDevices = todaySessionDeviceIds.size;

    return {
      activeDevices,
      totalDevices,
      newDevicesToday,
      totalObservations: observations.length,
      averageDwellTimeMs,
      peakHour,
      returningVisitors,
    };
  }, [devices, observations, sessions]);

  const value: AnalyticsContextType = {
    devices,
    observations,
    sessions,
    loading,
    error,
    resetting,
    ...metrics,
    resetSession,
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
