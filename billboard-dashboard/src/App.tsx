import { useState } from 'react';
import { useAnalytics } from './contexts/AnalyticsContext';
import { KPICard } from './components/KPICard';
import { LiveFeedTable } from './components/LiveFeedTable';
import { FootfallChart, DeviceTypeDistribution, CategoryDistribution, RSSIDistribution, DwellTimeChart } from './components/Charts';
import { Users, UserPlus, Database, Radio, Activity, RefreshCw, Clock, TrendingUp, UserCheck, AlertTriangle } from 'lucide-react';
import { formatTimeAgo, formatDuration } from './lib/utils';

function App() {
  const {
    loading,
    error,
    resetting,
    totalDevices,
    activeDevices,
    newDevicesToday,
    totalObservations,
    averageDwellTimeMs,
    peakHour,
    returningVisitors,
    observations,
    resetSession,
  } = useAnalytics();

  const [showConfirm, setShowConfirm] = useState(false);

  if (error) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="glass-card p-6 max-w-lg w-full text-center">
          <div className="w-12 h-12 bg-danger/20 text-danger rounded-full flex items-center justify-center mx-auto mb-4">
            <Activity />
          </div>
          <h2 className="text-xl font-bold mb-2">Connection Error</h2>
          <p className="text-textSecondary">{error}</p>
          <p className="text-sm text-textSecondary mt-4">
            Check your <code className="bg-white/10 px-1 rounded">.env</code> file for{' '}
            <code className="bg-white/10 px-1 rounded">VITE_SUPABASE_URL</code> and{' '}
            <code className="bg-white/10 px-1 rounded">VITE_SUPABASE_ANON_KEY</code>.
          </p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Activity className="w-8 h-8 text-primary animate-spin" />
          <p className="text-textSecondary animate-pulse">Connecting to Live Analytics...</p>
        </div>
      </div>
    );
  }

  const lastUpdate = observations.length > 0 ? observations[0].timestamp : null;

  return (
    <div className="min-h-screen bg-background text-textPrimary p-6">
      <div className="max-w-7xl mx-auto space-y-6">

        {/* Header */}
        <header className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Footfall Analytics Dashboard</h1>
            <p className="text-textSecondary text-sm">Real-time Bluetooth &amp; Wi-Fi device monitoring</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowConfirm(true)}
              disabled={resetting}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-xl bg-primary/20 text-primary hover:bg-primary/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <RefreshCw className={`w-4 h-4 ${resetting ? 'animate-spin' : ''}`} />
              {resetting ? 'Resetting…' : 'New Session'}
            </button>
            <div className="flex items-center gap-3 glass-card px-4 py-2 text-sm">
              <span className="flex h-3 w-3 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success opacity-75"></span>
                <span className="relative inline-flex rounded-full h-3 w-3 bg-success"></span>
              </span>
              <span className="font-medium">System Online</span>
              <span className="text-textSecondary ml-2 border-l border-white/10 pl-2">
                Last updated: {lastUpdate ? formatTimeAgo(lastUpdate) : 'Never'}
              </span>
            </div>
          </div>
        </header>

        {/* KPI Grid — Row 1 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <KPICard
            title="Visitors Today"
            value={totalDevices.toLocaleString()}
            icon={Users}
          />
          <KPICard
            title="Currently Present (5 min)"
            value={activeDevices.length.toLocaleString()}
            icon={Radio}
            className="border-primary/50"
          />
          <KPICard
            title="New Devices Today"
            value={newDevicesToday.toLocaleString()}
            icon={UserPlus}
          />
          <KPICard
            title="Total Observations"
            value={totalObservations.toLocaleString()}
            icon={Database}
          />
        </div>

        {/* KPI Grid — Row 2 (session-based metrics) */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <KPICard
            title="Avg Dwell Time"
            value={averageDwellTimeMs > 0 ? formatDuration(averageDwellTimeMs) : '—'}
            icon={Clock}
          />
          <KPICard
            title="Peak Hour Today"
            value={peakHour}
            icon={TrendingUp}
          />
          <KPICard
            title="Returning Visitors Today"
            value={returningVisitors.toLocaleString()}
            icon={UserCheck}
          />
        </div>

        {/* Main Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Charts Section */}
          <div className="lg:col-span-2 space-y-6">
            <FootfallChart />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <DeviceTypeDistribution />
              <CategoryDistribution />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <RSSIDistribution />
              <DwellTimeChart />
            </div>
          </div>

          {/* Live Feed Section */}
          <div className="lg:col-span-1 h-[800px]">
            <LiveFeedTable />
          </div>
        </div>

      </div>

      {/* New Session confirmation dialog */}
      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="glass-card p-6 max-w-md w-full mx-4 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-danger/20 text-danger flex items-center justify-center shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-lg font-semibold">Start New Session?</h2>
                <p className="text-sm text-textSecondary">This will permanently delete all devices, observations, and sessions from the database.</p>
              </div>
            </div>
            <p className="text-sm text-textSecondary border border-white/10 rounded-lg px-3 py-2 bg-white/5">
              The Android app will continue scanning and repopulate the dashboard automatically once it syncs again.
            </p>
            <div className="flex gap-3 justify-end pt-1">
              <button
                onClick={() => setShowConfirm(false)}
                className="px-4 py-2 text-sm font-medium rounded-xl bg-white/10 hover:bg-white/20 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => { setShowConfirm(false); resetSession(); }}
                className="px-4 py-2 text-sm font-medium rounded-xl bg-danger/80 hover:bg-danger text-white transition-colors"
              >
                Yes, Reset Everything
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
