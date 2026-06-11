import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Distance estimation using standard Log-Distance Path Loss Model
// formula: d = 10 ^ ((TxPower - RSSI) / (10 * N))
// Using typical values: TxPower = -59 (at 1 meter), N = 2.0 (free space)
export function estimateDistance(rssi: number): number {
  const txPower = -59;
  const n = 2.0;
  const distance = Math.pow(10, (txPower - rssi) / (10 * n));
  return Number(distance.toFixed(2));
}

export function formatTimeAgo(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);
  
  if (diffInSeconds < 60) return `${diffInSeconds}s ago`;
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  return `${Math.floor(diffInSeconds / 86400)}d ago`;
}
