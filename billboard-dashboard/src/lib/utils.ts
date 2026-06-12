import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Distance estimation using the Log-Distance Path Loss Model.
 * d = 10 ^ ((TxPower - RSSI) / (10 * N))
 * TxPower = -59 dBm (typical at 1 m), N = 2.0 (free-space exponent)
 */
export function estimateDistance(rssi: number): number {
  const txPower = -59;
  const n = 2.0;
  const distance = Math.pow(10, (txPower - rssi) / (10 * n));
  return Number(distance.toFixed(1));
}

export function formatTimeAgo(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 5)  return 'just now';
  if (diffInSeconds < 60) return `${diffInSeconds}s ago`;
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
  return `${Math.floor(diffInSeconds / 86400)}d ago`;
}

/**
 * Formats a duration in milliseconds to a human-readable string.
 * e.g. 90000 → "1m 30s", 3661000 → "1h 1m"
 */
export function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return '0s';
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${seconds}s`;
  return `${seconds}s`;
}
