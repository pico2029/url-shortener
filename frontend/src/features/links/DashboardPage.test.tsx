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
  await screen.findByText('Caricamento dei link…');
  await act(async () => resolve(response(page)));
  expect(screen.getByLabelText('URL breve')).toHaveValue(link.shortUrl);
  expect(screen.getByText('Salvato')).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: link.shortUrl })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Precedente' })).toBeDisabled();
  fetchMock.mockResolvedValueOnce(response({ ...page, page: 1 }));
  await userEvent.click(screen.getByRole('button', { name: 'Successiva' }));
  await screen.findByText('Pagina 2 di 2');
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/links?page=1&size=20');
});

it('riprova il caricamento senza perdere la sessione', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(response({ ...page, items: [], totalElements: 0, totalPages: 0 }));
  render(<App />);
  await screen.findByText('Non è stato possibile caricare i tuoi link.');
  expect(sessionToken()).toBe('A');
  await userEvent.click(screen.getByRole('button', { name: 'Riprova' }));
  await screen.findByText('Non hai ancora creato link personali. Inizia dal form qui sopra.');
});

it('torna alla prima pagina dopo la creazione e distingue un errore di aggiornamento', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page)).mockResolvedValueOnce(response({ ...page, page: 1 }));
  render(<App />);
  await screen.findByText('Pagina 1 di 2');
  await userEvent.click(screen.getByRole('button', { name: 'Successiva' }));
  await screen.findByText('Pagina 2 di 2');
  fetchMock.mockResolvedValueOnce(response(link, 201)).mockRejectedValueOnce(new TypeError('offline'));
  await userEvent.type(screen.getByLabelText('URL da accorciare'), link.originalUrl);
  await userEvent.click(screen.getByRole('button', { name: 'Accorcia URL' }));
  await screen.findByText('Il link è stato creato, ma non è stato possibile aggiornare l’elenco.');
  expect(screen.getByText('Link creato correttamente.')).toBeInTheDocument();
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/links?page=0&size=20');
});

it('esce sostituendo la cronologia e impedisce di riaprire la dashboard', async () => {
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
  render(<App />);
  await screen.findByText('Salvato');
  const replace = vi.spyOn(window.history, 'replaceState');
  await userEvent.click(screen.getByRole('button', { name: 'Esci' }));
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
  expect(screen.getByText(/sessione è scaduta/)).toBeInTheDocument();
  vi.useRealTimers();
});
