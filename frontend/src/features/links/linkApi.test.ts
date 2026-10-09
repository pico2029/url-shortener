import { beforeEach, expect, it, vi } from 'vitest';
import { createLink } from './linkApi';
import { saveSession, sessionToken } from '../auth/session';

beforeEach(() => { sessionStorage.clear(); window.history.replaceState(null, '', '/'); });

it('invia il JWT anche dalla home e non riprova come anonimo dopo un 401', async () => {
  saveSession({ accessToken: 'A', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({ code: 'UNAUTHORIZED' }) });
  vi.stubGlobal('fetch', fetchMock);
  await expect(createLink('https://example.com', new AbortController().signal)).rejects.toHaveProperty('name', 'AbortError');
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer A');
  expect(sessionToken()).toBeNull();
  expect(window.location.pathname).toBe('/login');
});

it('crea senza Bearer quando non esiste una sessione', async () => {
  const link = { originalUrl: 'https://example.com', shortCode: 'Ab123456', shortUrl: 'http://localhost:8080/r/Ab123456', expiresAt: null };
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => link });
  vi.stubGlobal('fetch', fetchMock);
  await expect(createLink(link.originalUrl, new AbortController().signal)).resolves.toEqual(link);
  expect(new Headers(fetchMock.mock.calls[0][1].headers).has('Authorization')).toBe(false);
});
