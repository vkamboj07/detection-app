import { useMemo } from 'react';
import { useAnalytics } from '../contexts/AnalyticsContext';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  Legend,
} from 'recharts';
import { format, subMinutes } from 'date-fns';

const COLORS = ['#3B82F6', '#8B5CF6', '#10B981', '#F59E0B', '#EF4444'];

const TOOLTIP_STYLE = {
  contentStyle: {
    backgroundColor: '#1A1F2C',
    borderColor: 'rgba(255,255,255,0.1)',
    borderRadius: '8px',
  },
  itemStyle: { color: '#F3F4F6' },
};

// ---------------------------------------------------------------------------
// Footfall over time (last 60 minutes, 5-min buckets)
// ---------------------------------------------------------------------------
export function FootfallChart() {
  const { observations } = useAnalytics();

  const data = useMemo(() => {
    const now = new Date();
    const lastHour = subMinutes(now, 60);

    const recentObs = observations.filter(o => new Date(o.timestamp) >= lastHour);

    // Align to the current 5-minute boundary
    const alignedNow = new Date(now);
    alignedNow.setMinutes(Math.floor(now.getMinutes() / 5) * 5, 0, 0);

    // 12 buckets × 5 min = last 60 minutes
    const buckets: Record<string, { time: string; count: number; unique: Set<number> }> = {};
    for (let i = 0; i < 12; i++) {
      const bucketTime = subMinutes(alignedNow, i * 5);
      const key = format(bucketTime, 'HH:mm');
      buckets[key] = { time: key, count: 0, unique: new Set() };
    }

    recentObs.forEach(obs => {
      const time = new Date(obs.timestamp);
      const bucketTime = new Date(time);
      bucketTime.setMinutes(Math.floor(time.getMinutes() / 5) * 5, 0, 0);
      const key = format(bucketTime, 'HH:mm');
      if (buckets[key]) {
        buckets[key].count++;
        buckets[key].unique.add(obs.device_id);
      }
    });

    return Object.values(buckets)
      .map(b => ({ time: b.time, Observations: b.count, UniqueDevices: b.unique.size }))
      .reverse();
  }, [observations]);

  return (
    <div className="glass-card p-6 h-96">
      <h3 className="text-lg font-semibold mb-6">Footfall Over Time (Last Hour)</h3>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
          <XAxis dataKey="time" stroke="#9CA3AF" fontSize={12} tickLine={false} axisLine={false} />
          <YAxis stroke="#9CA3AF" fontSize={12} tickLine={false} axisLine={false} allowDecimals={false} />
          <Tooltip {...TOOLTIP_STYLE} />
          <Legend />
          <Line type="monotone" dataKey="Observations" stroke="#3B82F6" strokeWidth={2} dot={false} activeDot={{ r: 8 }} />
          <Line type="monotone" dataKey="UniqueDevices" stroke="#8B5CF6" strokeWidth={2} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Device type distribution (Wi-Fi vs BLE vs BT Classic)
// ---------------------------------------------------------------------------
export function DeviceTypeDistribution() {
  const { devices } = useAnalytics();

  const data = useMemo(() => {
    let wifi = 0;
    let ble = 0;
    let btClassic = 0;

    devices.forEach(d => {
      if (d.source?.toUpperCase().includes('WIFI')) wifi++;
      else if (d.source?.toUpperCase() === 'BLE') ble++;
      else btClassic++;
    });

    return [
      { name: 'Wi-Fi', value: wifi },
      { name: 'BLE', value: ble },
      { name: 'BT Classic', value: btClassic },
    ].filter(d => d.value > 0);
  }, [devices]);

  return (
    <div className="glass-card p-6 h-96">
      <h3 className="text-lg font-semibold mb-6">Device Types</h3>
      {data.length === 0 ? (
        <div className="flex items-center justify-center h-full text-textSecondary text-sm">
          No data yet
        </div>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              innerRadius={60}
              outerRadius={100}
              paddingAngle={5}
              dataKey="value"
            >
              {data.map((_, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip contentStyle={TOOLTIP_STYLE.contentStyle} />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// RSSI signal strength distribution
// ---------------------------------------------------------------------------
export function RSSIDistribution() {
  const { observations } = useAnalytics();

  const data = useMemo(() => {
    const buckets = {
      'Excellent (>-60)': 0,
      'Good (-60–-70)': 0,
      'Fair (-70–-80)': 0,
      'Poor (<-80)': 0,
    };

    observations.forEach(obs => {
      if (obs.rssi >= -60)       buckets['Excellent (>-60)']++;
      else if (obs.rssi >= -70)  buckets['Good (-60–-70)']++;
      else if (obs.rssi >= -80)  buckets['Fair (-70–-80)']++;
      else                      buckets['Poor (<-80)']++;
    });

    return Object.entries(buckets).map(([name, value]) => ({ name, value }));
  }, [observations]);

  return (
    <div className="glass-card p-6 h-96">
      <h3 className="text-lg font-semibold mb-6">Signal Strength (RSSI)</h3>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" horizontal={true} vertical={false} />
          <XAxis type="number" stroke="#9CA3AF" fontSize={12} tickLine={false} axisLine={false} allowDecimals={false} />
          <YAxis dataKey="name" type="category" stroke="#9CA3AF" fontSize={11} tickLine={false} axisLine={false} width={110} />
          <Tooltip
            cursor={{ fill: 'rgba(255,255,255,0.05)' }}
            contentStyle={TOOLTIP_STYLE.contentStyle}
          />
          <Bar dataKey="value" radius={[0, 4, 4, 0]}>
            {data.map((_, index) => (
              <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Dwell time distribution from sessions (buckets: <1m, 1-5m, 5-15m, 15m+)
// ---------------------------------------------------------------------------
export function DwellTimeChart() {
  const { sessions } = useAnalytics();

  const data = useMemo(() => {
    const buckets = {
      '< 1 min': 0,
      '1 – 5 min': 0,
      '5 – 15 min': 0,
      '> 15 min': 0,
    };

    sessions.forEach(s => {
      const mins = (s.duration ?? 0) / 60000;
      if (mins < 1)        buckets['< 1 min']++;
      else if (mins < 5)   buckets['1 – 5 min']++;
      else if (mins < 15)  buckets['5 – 15 min']++;
      else                 buckets['> 15 min']++;
    });

    return Object.entries(buckets).map(([name, value]) => ({ name, value }));
  }, [sessions]);

  const hasData = data.some(d => d.value > 0);

  return (
    <div className="glass-card p-6 h-72">
      <h3 className="text-lg font-semibold mb-6">Dwell Time Distribution</h3>
      {!hasData ? (
        <div className="flex items-center justify-center h-full text-textSecondary text-sm">
          No session data yet — sessions sync from the Android app
        </div>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 5, right: 30, left: 10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
            <XAxis dataKey="name" stroke="#9CA3AF" fontSize={12} tickLine={false} axisLine={false} />
            <YAxis stroke="#9CA3AF" fontSize={12} tickLine={false} axisLine={false} allowDecimals={false} />
            <Tooltip
              cursor={{ fill: 'rgba(255,255,255,0.05)' }}
              contentStyle={TOOLTIP_STYLE.contentStyle}
            />
            <Bar dataKey="value" fill="#10B981" radius={[4, 4, 0, 0]}>
              {data.map((_, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
