import { useAnalytics } from '../contexts/AnalyticsContext';
import { estimateDistance, formatTimeAgo, cn } from '../lib/utils';
import { Wifi, Bluetooth, Activity } from 'lucide-react';

export function LiveFeedTable() {
  const { observations, devices } = useAnalytics();

  // Show latest 50 observations
  const recentObs = observations.slice(0, 50);

  // Helper to get device info
  const getDevice = (deviceId: number) => devices.find((d) => d.id === deviceId);

  return (
    <div className="glass-card flex flex-col h-full overflow-hidden">
      <div className="p-6 border-b border-white/10 flex items-center justify-between">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Activity className="w-5 h-5 text-primary animate-pulse" />
          Live Device Activity Feed
        </h2>
        <span className="text-xs px-2 py-1 bg-primary/20 text-primary rounded-full animate-pulse">
          Live Updates
        </span>
      </div>
      <div className="overflow-x-auto overflow-y-auto flex-1">
        <table className="w-full text-sm text-left">
          <thead className="text-xs text-textSecondary uppercase bg-white/5 sticky top-0 z-10 backdrop-blur-md">
            <tr>
              <th className="px-6 py-4 font-medium">Time</th>
              <th className="px-6 py-4 font-medium">Type</th>
              <th className="px-6 py-4 font-medium">Device ID</th>
              <th className="px-6 py-4 font-medium">RSSI</th>
              <th className="px-6 py-4 font-medium">Distance</th>
            </tr>
          </thead>
          <tbody>
            {recentObs.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-textSecondary">
                  Waiting for observations...
                </td>
              </tr>
            ) : (
              recentObs.map((obs) => {
                const device = getDevice(obs.device_id);
                const isWifi = obs.source.includes('WIFI');
                
                return (
                  <tr 
                    key={obs.id} 
                    className="border-b border-white/5 hover:bg-white/5 transition-colors animate-in fade-in slide-in-from-top-2 duration-300"
                  >
                    <td className="px-6 py-3 whitespace-nowrap text-textSecondary">
                      {formatTimeAgo(obs.timestamp)}
                    </td>
                    <td className="px-6 py-3">
                      <div className="flex items-center gap-2">
                        {isWifi ? (
                          <Wifi className="w-4 h-4 text-primary" />
                        ) : (
                          <Bluetooth className="w-4 h-4 text-secondary" />
                        )}
                        <span className={cn(
                          "px-2 py-0.5 rounded text-xs",
                          isWifi ? "bg-primary/20 text-primary" : "bg-secondary/20 text-secondary"
                        )}>
                          {obs.source}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-3 font-mono text-xs">
                      {device?.device_identifier || 'Unknown'}
                    </td>
                    <td className="px-6 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-16 h-2 bg-white/10 rounded-full overflow-hidden">
                          <div 
                            className="h-full bg-success"
                            style={{ width: `${Math.max(0, Math.min(100, 100 - Math.abs(obs.rssi + 30)))}%` }}
                          />
                        </div>
                        <span className="text-textSecondary">{obs.rssi} dBm</span>
                      </div>
                    </td>
                    <td className="px-6 py-3 text-textSecondary">
                      ~{estimateDistance(obs.rssi)}m
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
