import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { saveSession } from '../auth/session';

const link = { originalUrl: 'https://example.com/page', shortCode: 'Ab123456', shortUrl: 'http://localhost:8080/r/Ab123456', createdAt: '2026-10-08T10:00:00Z', status: 'SAVED' };
const fetchMock = vi.fn();
const writeText = vi.fn();
const descriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
const response = (body: unknown) => ({ ok: true, status: 200, json: async () => body });

beforeEach(() => {
  fetchMock.mockReset();
  writeText.mockReset().mockResolvedValue(undefined);
  vi.stubGlobal('fetch', fetchMock);
  sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  sessionStorage.clear();
  if (descriptor) Object.defineProperty(navigator, 'clipboard', descriptor);
  else Reflect.deleteProperty(navigator, 'clipboard');
});

function mockClipboard() {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
}

it.each([false, true])('copies a newly created link (authenticated: %s) without extra requests', async authenticated => {
  const user = userEvent.setup();
  mockClipboard();
  window.history.replaceState(null, '', authenticated ? '/dashboard' : '/');
  if (authenticated) {
    saveSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
    fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
  }
  render(<App />);
  if (authenticated) await screen.findByText('You have no personal links yet. Create one using the form above.');
  expect(screen.queryByRole('button', { name: 'Copy link' })).not.toBeInTheDocument();
  fetchMock.mockResolvedValueOnce(response(link));
  if (authenticated) fetchMock.mockResolvedValueOnce(response({ items: [link], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
  await user.type(screen.getByLabelText('URL to shorten'), link.originalUrl);
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  await screen.findByLabelText('Full short URL');
  if (authenticated) await screen.findByText('Saved');
  const result = screen.getByRole('region', { name: 'Your short link' });
  const requestCount = fetchMock.mock.calls.length;
  await user.click(within(result).getByRole('button', { name: 'Copy link' }));
  expect(within(result).getByText('Link copied.')).toBeInTheDocument();
  expect(writeText).toHaveBeenCalledExactlyOnceWith(link.shortUrl);
  expect(fetchMock).toHaveBeenCalledTimes(requestCount);
  const input = within(result).getByLabelText('Full short URL');
  expect(input).toHaveValue(link.shortUrl);
  fireEvent.focus(input);
  expect(input).toHaveProperty('selectionEnd', link.shortUrl.length);
});

it('copies the correct dashboard row with independent feedback and no extra requests', async () => {
  const user = userEvent.setup();
  mockClipboard();
  saveSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  window.history.replaceState(null, '', '/dashboard');
  const other = { ...link, shortCode: 'Cd654321', shortUrl: 'http://localhost:8080/r/Cd654321' };
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response({ items: [link, other], page: 0, size: 20, totalElements: 2, totalPages: 1 }));
  render(<App />);
  await screen.findAllByText('Saved');
  const rows = screen.getAllByRole('listitem');
  await user.click(within(rows[1]).getByRole('button', { name: 'Copy link' }));
  expect(writeText).toHaveBeenCalledExactlyOnceWith(other.shortUrl);
  expect(within(rows[1]).getByRole('status')).toHaveTextContent('Link copied.');
  expect(within(rows[0]).getByRole('status')).toBeEmptyDOMElement();
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
