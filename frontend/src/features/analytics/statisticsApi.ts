import { authenticatedJson } from '../auth/authenticatedApi';

export type DailyClicks = { date: string; clicks: number };
export type LinkStatistics = {
  shortCode: string;
  totalClicks: number;
  timezone: 'UTC';
  from: string;
  to: string;
  daily: DailyClicks[];
};

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object';
}

function count(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function day(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function valid(value: unknown, shortCode: string): value is LinkStatistics {
  if (!object(value) || value.shortCode !== shortCode || value.timezone !== 'UTC'
    || !count(value.totalClicks) || !day(value.from) || !day(value.to)
    || !Array.isArray(value.daily) || value.daily.length !== 30) return false;
  const start = Date.parse(`${value.from}T00:00:00Z`);
  let sum = 0;
  for (let index = 0; index < value.daily.length; index++) {
    const entry: unknown = value.daily[index];
    if (!object(entry) || !day(entry.date) || !count(entry.clicks)
      || entry.date !== new Date(start + index * 86400000).toISOString().slice(0, 10)) return false;
    sum += entry.clicks;
  }
  return value.to === new Date(start + 29 * 86400000).toISOString().slice(0, 10)
    && Number.isSafeInteger(sum) && sum <= value.totalClicks;
}

export async function getStatistics(shortCode: string, signal: AbortSignal): Promise<LinkStatistics> {
  const result = await authenticatedJson<unknown>(`/api/v1/links/${encodeURIComponent(shortCode)}/stats`, { signal });
  if (!valid(result, shortCode)) throw new Error('Unexpected statistics response');
  return result;
}
