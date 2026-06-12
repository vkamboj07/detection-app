# Footfall Analytics Dashboard

Real-time web dashboard for the Billboard Analytics Android app. Shows live device footfall, dwell time, signal strength, and device type breakdowns powered by Supabase.

## Setup

### 1. Create Supabase tables

Run the SQL in `/supabase_schema.sql` (at the root of the repo) in your Supabase project's SQL editor.

### 2. Configure environment variables

Copy `.env` to `.env.local` and fill in your project credentials:

```bash
VITE_SUPABASE_URL=https://<your-project>.supabase.co
VITE_SUPABASE_ANON_KEY=<your-anon-key>
```

> The `.env.local` file is gitignored. Never commit your keys.

### 3. Install & run

```bash
npm install
npm run dev
```

### 4. Build for production

```bash
npm run build
npm run preview
```

## Features

- **Live feed** — real-time observation stream via Supabase Realtime
- **KPI cards** — total devices, currently present, new today, dwell time, peak hour, returning visitors
- **Footfall chart** — observations and unique devices over the last hour (5-min buckets)
- **Device type donut** — Wi-Fi vs BLE vs BT Classic breakdown
- **RSSI bar chart** — signal strength quality distribution
- **Dwell time chart** — session length distribution from synced Android sessions
