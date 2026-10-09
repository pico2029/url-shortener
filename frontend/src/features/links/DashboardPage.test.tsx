import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { saveSession, sessionToken } from '../auth/session';

const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body });
const link = { originalUrl: 'https://example.com/private', shortCode: 'Ab123456', shortUrl: 'http://localhost:8080/r/Ab123456', createdAt: '2026-10-08T10:00:00Z', status: 'SAVED' };
const page = { items: [link], page: 0, size: 20, totalElements: 21, totalPages: 2 };
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  saveSession({ accessToken: 'A', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  window.history.replaceState(null, '', '/dashboard');
});

it('mostra caricamento, link selezionabili, stato e paginazione', async () => {
  let resolve!: (value: unknown) => void;
  fetchMock.mockResolvedValueOnce(response({})).mockImplementationOnce(() => new Promise(r => { resolve = r; }));
  render(<App />);
  await screen.findByText('Loading your links…');
  await act(async () => resolve(response(page)));
  expect(screen.getByLabelText('Short URL')).toHaveValue(link.shortUrl);
  expect(screen.getByText('Saved')).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: link.shortUrl })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
  fetchMock.mockResolvedValueOnce(response({ ...page, page: 1 }));
  await userEvent.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('Page 2 of 2');
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/links?page=1&size=20');
});

it('riprova il caricamento senza perdere la sessione', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(response({ ...page, items: [], totalElements: 0, totalPages: 0 }));
  render(<App />);
  await screen.findByText('Your links could not be loaded.');
  expect(sessionToken()).toBe('A');
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByText('You have no personal links yet. Create one using the form above.');
});

it('torna alla prima pagina dopo la creazione e distingue un errore di aggiornamento', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page)).mockResolvedValueOnce(response({ ...page, page: 1 }));
  render(<App />);
  await screen.findByText('Page 1 of 2');
  await userEvent.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('Page 2 of 2');
  fetchMock.mockResolvedValueOnce(response(link, 201)).mockRejectedValueOnce(new TypeError('offline'));
  await userEvent.type(screen.getByLabelText('URL to shorten'), link.originalUrl);
  await userEvent.type(screen.getByLabelText('Custom alias (optional)'), 'Personal-Promo');
  await userEvent.click(screen.getByRole('button', { name: 'Shorten URL' }));
  await screen.findByText('Your link was created, but the list could not be refreshed.');
  expect(screen.getByText('Link saved successfully.')).toBeInTheDocument();
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/links?page=0&size=20');
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/links', expect.objectContaining({
    headers: expect.objectContaining({ authorization: 'Bearer A' }),
    body: JSON.stringify({ originalUrl: link.originalUrl, alias: 'Personal-Promo' }),
  }));
});

it('esce sostituendo la cronologia e impedisce di riaprire la dashboard', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
  render(<App />);
  await screen.findByText('Saved');
  const replace = vi.spyOn(window.history, 'replaceState');
  await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
  expect(replace).toHaveBeenCalledWith(null, '', '/login');
  expect(sessionToken()).toBeNull();
  expect(screen.queryByText(link.originalUrl)).not.toBeInTheDocument();
  act(() => {
    window.history.replaceState(null, '', '/dashboard');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
  replace.mockRestore();
});

it('termina una sessione quando scade durante la visualizzazione', async () => {
  vi.useFakeTimers();
  saveSession({ accessToken: 'A', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 5000).toISOString() });
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
  render(<App />);
  await act(async () => { await vi.advanceTimersByTimeAsync(5001); });
  expect(window.location.pathname).toBe('/login');
  expect(sessionToken()).toBeNull();
  expect(screen.getByText(/session has expired/)).toBeInTheDocument();
  vi.useRealTimers();
});
