import { createClient } from '@supabase/supabase-js';
import type { Database } from '../types/supabase';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

// Validate at module load time so the error surfaces in the UI (via AnalyticsContext)
// rather than crashing the entire React tree with an unhandled exception.
export const supabaseConfigError: string | null =
  !supabaseUrl || !supabaseAnonKey
    ? 'Missing Supabase environment variables. Add VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY to your .env file.'
    : null;

// Always export a client so imports never fail — the config error is surfaced by the UI.
export const supabase = createClient<Database>(
  supabaseUrl ?? 'https://placeholder.supabase.co',
  supabaseAnonKey ?? 'placeholder-key'
);
