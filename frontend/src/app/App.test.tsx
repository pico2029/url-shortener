import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App } from './App';
import { saveSession } from '../features/auth/session';

const fetchMock = vi.fn();
const session = { accessToken: 'signed-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() };
const emptyPage = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const response = (status: number, body: unknown) => ({ ok: status < 400, status, json: async () => body });
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  sessionStorage.clear();
  window.history.replaceState(null, '', '/login');
});
afterEach(() => vi.unstubAllGlobals());

it('mantiene le pagine refattorizzate durante la navigazione tra registrazione, login e home', async () => {
  window.history.replaceState(null, '', '/register');
  render(<App />);
  const user = userEvent.setup();
  const header = screen.getByRole('banner');

  await user.click(screen.getByRole('link', { name: 'Log in' }));
  expect(window.location.pathname).toBe('/login');
  expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
  expect(screen.getByRole('main')).toHaveClass('account-layout');

  await user.click(screen.getByRole('link', { name: 'Back to home' }));
  expect(window.location.pathname).toBe('/');
  expect(screen.getByRole('main')).toHaveClass('home-page');

  await user.click(screen.getByRole('link', { name: 'Create an account' }));
  expect(window.location.pathname).toBe('/register');
  expect(screen.getByRole('heading', { name: 'Create an account' })).toBeInTheDocument();

  await user.click(screen.getByRole('link', { name: 'Shorten a URL' }));
  await user.click(screen.getByRole('link', { name: 'Register' }));
  await user.click(screen.getByRole('link', { name: 'URL Shortener home' }));
  await user.click(screen.getByRole('link', { name: 'Log in' }));
  await user.click(screen.getByRole('link', { name: 'Register' }));
  expect(window.location.pathname).toBe('/register');
  expect(screen.getByRole('banner')).toBe(header);
  expect(fetchMock).not.toHaveBeenCalled();

  act(() => {
    window.history.replaceState(null, '', '/login');
    window.dispatchEvent(new PopStateEvent('popstate'));
  });
  expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
});

it('accede e naviga alla dashboard personale', async () => {
  fetchMock.mockResolvedValueOnce(response(200, session)).mockResolvedValueOnce(response(200, { email: 'user@example.com' })).mockResolvedValueOnce(response(200, emptyPage));
  render(<App />);
  const user = userEvent.setup();
  expect(screen.queryByLabelText('Confirm password')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute('href', '/register');
  await user.type(screen.getByLabelText('Email'), 'USER@EXAMPLE.COM');
  await user.type(screen.getByLabelText('Password'), 'una password lunga');
  await user.click(screen.getByRole('button', { name: 'Log in' }));
  await screen.findByText('You have no personal links yet. Create one using the form above.');
  expect(screen.getByLabelText('Custom alias (optional)')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  expect(window.location.pathname).toBe('/dashboard');
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/me', expect.objectContaining({ headers: expect.objectContaining({ authorization: 'Bearer signed-token' }) }));
});

it('mostra credenziali errate senza navigare', async () => {
  fetchMock.mockResolvedValue(response(401, { code: 'INVALID_CREDENTIALS' }));
  render(<App />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), 'user@example.com');
  await user.type(screen.getByLabelText('Password'), 'wrong password');
  await user.click(screen.getByRole('button', { name: 'Log in' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.');
  expect(window.location.pathname).toBe('/login');
  expect(sessionStorage.length).toBe(0);
});

it('blocca la dashboard senza sessione', async () => {
  window.history.replaceState(null, '', '/dashboard');
  render(<App />);
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
  expect(screen.getByLabelText('Password')).toBeInTheDocument();
  expect(fetchMock).not.toHaveBeenCalled();
});

it('mantiene la sessione dopo il refresh e mostra i link personali', async () => {
  saveSession(session);
  window.history.replaceState(null, '', '/dashboard');
  fetchMock.mockResolvedValueOnce(response(200, {})).mockResolvedValueOnce(response(200, emptyPage));
  render(<App />);
  await screen.findByText('You have no personal links yet. Create one using the form above.');
  expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
});

it('rimuove una sessione rifiutata dal server e ritorna ad Log in', async () => {
  saveSession(session);
  window.history.replaceState(null, '', '/dashboard');
  fetchMock.mockResolvedValue(response(401, {}));
  render(<App />);
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
  expect(sessionStorage.length).toBe(0);
});

it('rifiuta un token già scaduto senza contattare il server', async () => {
  saveSession({ ...session, expiresAt: new Date(Date.now() - 1000).toISOString() });
  window.history.replaceState(null, '', '/dashboard');
  render(<App />);
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
  expect(fetchMock).not.toHaveBeenCalled();
});

it('non mostra il form prima della verifica della sessione', async () => {
  saveSession(session);
  window.history.replaceState(null, '', '/dashboard');
  let complete: ((value: ReturnType<typeof response>) => void) | undefined;
  fetchMock.mockReturnValue(new Promise(resolve => { complete = resolve; }));
  render(<App />);
  expect(screen.getByRole('status')).toHaveTextContent('Checking your session');
  expect(screen.queryByLabelText('Custom alias (optional)')).not.toBeInTheDocument();
  complete?.(response(200, {}));
  expect(await screen.findByLabelText('Custom alias (optional)')).toBeInTheDocument();
});

it('invia il Bearer e rimuove la sessione se la creazione restituisce 401', async () => {
  saveSession(session);
  window.history.replaceState(null, '', '/dashboard');
  fetchMock.mockResolvedValueOnce(response(200, {})).mockResolvedValueOnce(response(200, emptyPage))
    .mockResolvedValueOnce(response(401, { code: 'UNAUTHORIZED', detail: 'Expired' }));
  render(<App />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('URL to shorten'), 'https://example.com');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  await waitFor(() => expect(window.location.pathname).toBe('/login'));
  expect(sessionStorage.length).toBe(0);
  expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/links', expect.objectContaining({
    headers: expect.objectContaining({ authorization: 'Bearer signed-token' }),
    body: JSON.stringify({ originalUrl: 'https://example.com' }),
  }));
});
