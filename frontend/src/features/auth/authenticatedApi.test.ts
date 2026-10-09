import { beforeEach, expect, it, vi } from 'vitest';
import { authenticatedJson, endSession } from './authenticatedApi';
import { saveSession, sessionToken } from './session';

const session = (accessToken = 'A') => ({ accessToken, tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
beforeEach(() => { sessionStorage.clear(); window.history.replaceState(null, '', '/dashboard'); });

it('ignora un 401 tardivo della sessione precedente', async () => {
  let resolve!: (value: unknown) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise(r => { resolve = r; })));
  saveSession(session());
  const request = authenticatedJson('/api/v1/links');
  const assertion = expect(request).rejects.toHaveProperty('name', 'AbortError');
  saveSession(session('B'));
  resolve({ ok: false, status: 401, json: async () => ({}) });
  await assertion;
  expect(sessionToken()).toBe('B');
  expect(window.location.pathname).toBe('/dashboard');
});

it('conserva la sessione in caso di errore di rete', async () => {
  saveSession(session());
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
  await expect(authenticatedJson('/api/v1/links')).rejects.toThrow('offline');
  expect(sessionToken()).toBe('A');
});

it('annulla le richieste al logout e scarta anche risultati già in volo', async () => {
  let resolve!: (value: unknown) => void;
  const fetchMock = vi.fn((_path: string, options: RequestInit) => {
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer A');
    return new Promise(r => { resolve = r; });
  });
  vi.stubGlobal('fetch', fetchMock);
  saveSession(session());
  const request = authenticatedJson('/api/v1/links');
  const assertion = expect(request).rejects.toHaveProperty('name', 'AbortError');
  endSession();
  expect(fetchMock.mock.calls[0][1].signal?.aborted).toBe(true);
  expect(sessionToken()).toBeNull();
  expect(window.location.pathname).toBe('/login');
  resolve({ ok: true, json: async () => ({ private: true }) });
  await assertion;
});

it('propaga AbortSignal senza invalidare la sessione', async () => {
  let resolve!: (value: unknown) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise(r => { resolve = r; })));
  saveSession(session());
  const controller = new AbortController();
  const request = authenticatedJson('/api/v1/links', { signal: controller.signal });
  const assertion = expect(request).rejects.toHaveProperty('name', 'AbortError');
  controller.abort();
  resolve({ ok: true, json: async () => ({}) });
  await assertion;
  expect(sessionToken()).toBe('A');
});

it('isola due accessi anche quando ricevono lo stesso JWT', async () => {
  let resolve!: (value: unknown) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise(r => { resolve = r; })));
  saveSession(session());
  const request = authenticatedJson('/api/v1/links');
  const assertion = expect(request).rejects.toHaveProperty('name', 'AbortError');
  saveSession(session());
  resolve({ ok: false, status: 401, json: async () => ({}) });
  await assertion;
  expect(sessionToken()).toBe('A');
  expect(window.location.pathname).toBe('/dashboard');
});
