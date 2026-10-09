export const browserTimeZone = () => new Intl.DateTimeFormat().resolvedOptions().timeZone;

export function expirationInstant(value: string): string | null {
  if (!value) return null;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Invalid expiration');
  const date = new Date(value);
  const parts = [date.getFullYear(), date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes()];
  const expected = value.split(/[-T:]/).map(Number);
  if (!Number.isFinite(date.getTime()) || !parts.every((part, index) => part === expected[index])
      || date.getTime() <= Date.now()) throw new Error('Invalid expiration');
  return date.toISOString();
}
