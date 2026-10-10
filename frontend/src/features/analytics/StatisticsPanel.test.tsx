import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { saveSession, sessionToken } from '../auth/session';
import { getStatistics } from './statisticsApi';

const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body });
const link = { originalUrl: 'https://example.com/private', shortCode: 'StatsOne', shortUrl: 'http://localhost:8080/r/StatsOne', expiresAt: null, createdAt: '2026-10-08T10:00:00Z', status: 'SAVED' };
const second = { ...link, shortCode: 'StatsTwo', status: 'EXPIRED' };
const page = { items: [link, second], page: 0, size: 20, totalElements: 21, totalPages: 2 };
const statistics = (code = link.shortCode, total = 0) => ({ shortCode: code, totalClicks: total, timezone: 'UTC',
  from: '2026-09-11', to: '2026-10-10', daily: Array.from({ length: 30 }, (_, index) => ({
    date: new Date(Date.UTC(2026, 8, 11 + index)).toISOString().slice(0, 10), clicks: index === 29 ? total : 0,
  })),
});
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  saveSession({ accessToken: 'A', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  window.history.replaceState(null, '', '/dashboard');
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
});

it('loads on demand, shows zero and UTC daily values, refreshes and closes', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  expect(fetchMock).toHaveBeenCalledTimes(2);
  let finish = (_value: unknown) => {};
  const pending = new Promise(resolve => { finish = resolve; });
  fetchMock.mockImplementationOnce(() => pending);
  await userEvent.click(buttons[0]);
  expect(screen.getByText('Loading statistics…')).toBeInTheDocument();
  await act(async () => finish(response(statistics())));
  await screen.findByText('No clicks recorded yet.');
  expect(screen.getByText('Last 30 days · UTC')).toBeInTheDocument();
  expect(screen.getByText('Maximum: 0 clicks per day')).toBeInTheDocument();
  expect(fetchMock.mock.calls.at(-1)?.[1]).toEqual(expect.objectContaining({ headers: expect.objectContaining({ authorization: 'Bearer A' }) }));
  await userEvent.click(screen.getByText('Daily counts'));
  expect(screen.getByRole('table')).toBeInTheDocument();
  expect(screen.getByText('2026-09-11')).toBeInTheDocument();
  fetchMock.mockResolvedValueOnce(response(statistics(link.shortCode, 3)));
  const refresh = screen.getByRole('button', { name: 'Refresh' });
  await userEvent.click(refresh);
  await waitFor(() => expect(screen.queryByText('No clicks recorded yet.')).not.toBeInTheDocument());
  expect(screen.getByText('Total clicks').querySelector('strong')).toHaveTextContent('3');
  expect(refresh).toHaveFocus();
  await userEvent.click(screen.getByRole('button', { name: 'Close statistics' }));
  expect(screen.queryByText('Click statistics')).not.toBeInTheDocument();
});

it('supports expired links and cancels stale responses when changing the selected link', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  let finish = (_value: unknown) => {};
  const pending = new Promise(resolve => { finish = resolve; });
  fetchMock.mockImplementationOnce(() => pending);
  await userEvent.click(buttons[0]);
  const signal = fetchMock.mock.calls.at(-1)?.[1].signal as AbortSignal;
  fetchMock.mockResolvedValueOnce(response(statistics(second.shortCode, 2)));
  await userEvent.click(buttons[1]);
  expect(signal.aborted).toBe(true);
  await screen.findByText('Total clicks');
  await act(async () => finish(response(statistics(link.shortCode, 99))));
  expect(screen.getAllByText('Click statistics')).toHaveLength(1);
  expect(screen.getByText('Total clicks').querySelector('strong')).toHaveTextContent('2');
});

it('offers retry for service errors and malformed responses', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  fetchMock.mockResolvedValueOnce(response({ code: 'SERVICE_UNAVAILABLE' }, 503));
  await userEvent.click(buttons[0]);
  await screen.findByText('Statistics could not be loaded. Please try again.');
  expect(sessionToken()).toBe('A');
  fetchMock.mockResolvedValueOnce(response({ ...statistics(), daily: [] }));
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByText('Statistics could not be loaded. Please try again.');
  fetchMock.mockResolvedValueOnce(response(statistics()));
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByText('No clicks recorded yet.');
});

it('cancels pending statistics on pagination and logout', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  fetchMock.mockImplementationOnce(() => new Promise(() => {}));
  await userEvent.click(buttons[0]);
  const signal = fetchMock.mock.calls.at(-1)?.[1].signal as AbortSignal;
  fetchMock.mockResolvedValueOnce(response({ ...page, page: 1 }));
  await userEvent.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('Page 2 of 2');
  expect(signal.aborted).toBe(true);
  fetchMock.mockImplementationOnce(() => new Promise(() => {}));
  await userEvent.click(screen.getAllByRole('button', { name: 'Statistics' })[0]);
  const logoutSignal = fetchMock.mock.calls.at(-1)?.[1].signal as AbortSignal;
  await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
  expect(logoutSignal.aborted).toBe(true);
  expect(screen.queryByText('Click statistics')).not.toBeInTheDocument();
});

it('expires the session on 401 from the statistics API', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  fetchMock.mockResolvedValueOnce(response({ code: 'UNAUTHORIZED' }, 401));
  await userEvent.click(buttons[0]);
  await waitFor(() => expect(window.location.pathname).toContain('/login'));
  expect(sessionToken()).toBeNull();
});

it('uses keyboard controls and cancels statistics when deletion starts', async () => {
  render(<App />);
  const buttons = await screen.findAllByRole('button', { name: 'Statistics' });
  fetchMock.mockImplementationOnce(() => new Promise(() => {}));
  buttons[0].focus();
  await userEvent.keyboard('{Enter}');
  expect(buttons[0]).toHaveAttribute('aria-expanded', 'true');
  const signal = fetchMock.mock.calls.at(-1)?.[1].signal as AbortSignal;
  const row = buttons[0].closest('li');
  if (!row) throw new Error('Link row missing');
  await userEvent.click(within(row).getByRole('button', { name: 'Delete link' }));
  fetchMock.mockImplementationOnce(() => new Promise(() => {}));
  await userEvent.click(screen.getByRole('button', { name: 'Delete permanently' }));
  expect(signal.aborted).toBe(true);
  expect(screen.queryByText('Click statistics')).not.toBeInTheDocument();
});

it('rejects invalid UTC ranges, negative counts and mismatched codes', async () => {
  fetchMock.mockReset();
  const valid = statistics();
  for (const bad of [{ ...valid, shortCode: 'wrong' }, { ...valid, totalClicks: -1 },
    { ...valid, timezone: 'Europe/Rome' }, { ...valid, to: '2026-10-11' },
    { ...valid, daily: valid.daily.map((day, index) => index === 0 ? { ...day, date: '2026-02-30' } : day) }]) {
    fetchMock.mockResolvedValueOnce(response(bad));
    await expect(getStatistics(link.shortCode, new AbortController().signal)).rejects.toThrow('Unexpected statistics response');
  }
});
