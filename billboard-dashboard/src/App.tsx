import { useAnalytics } from './contexts/AnalyticsContext';
import { KPICard } from './components/KPICard';
import { LiveFeedTable } from './components/LiveFeedTable';
import { FootfallChart, DeviceTypeDistribution, RSSIDistribution } from './components/Charts';
import { Users, UserPlus, Database, Radio, Activity } from 'lucide-react';
import { formatTimeAgo } from './lib/utils';

function App() {
  const { 
    loading, 
    error, 
    totalDevices, 
    activeDevices, 
    newDevicesToday, 
    totalObservations,
    observations 
  } = useAnalytics();

  if (error) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="glass-card p-6 max-w-lg w-full text-center">
          <div className="w-12 h-12 bg-danger/20 text-danger rounded-full flex items-center justify-center mx-auto mb-4">
            <Activity />
          </div>
          <h2 className="text-xl font-bold mb-2">Connection Error</h2>
          <p className="text-textSecondary">{error}</p>
          <p className="text-sm text-textSecondary mt-4">Please check your Supabase credentials in .env.local</p>
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
            <p className="text-textSecondary text-sm">Real-time Bluetooth & Wi-Fi device monitoring</p>
          </div>
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
        </header>

        {/* KPI Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <KPICard 
            title="Total Devices Seen" 
            value={totalDevices.toLocaleString()} 
            icon={Users} 
          />
          <KPICard 
            title="Currently Present (5m)" 
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

        {/* Main Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Charts Section */}
          <div className="lg:col-span-2 space-y-6">
            <FootfallChart />
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <DeviceTypeDistribution />
              <RSSIDistribution />
            </div>
          </div>

          {/* Live Feed Section */}
          <div className="lg:col-span-1 h-[800px]">
            <LiveFeedTable />
          </div>
        </div>

      </div>
    </div>
  );
}

export default App;
