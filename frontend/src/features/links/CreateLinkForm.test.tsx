import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CreateLinkForm } from './CreateLinkForm';
import { normalizeAlias, validAlias } from './alias';
import { readSession, saveSession } from '../auth/session';

const fetchMock = vi.fn();
const response = (status: number, body: unknown) => ({ ok: status < 400, status, json: async () => body });
const created = { originalUrl: 'https://example.com', shortCode: 'Promo-2026', shortUrl: 'http://localhost:8080/r/Promo-2026' };
beforeEach(() => { fetchMock.mockReset(); sessionStorage.clear(); window.history.replaceState(null, '', '/'); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => vi.unstubAllGlobals());

it('keeps anonymous creation free of alias and Authorization', async () => {
  fetchMock.mockResolvedValue(response(201, created));
  render(<CreateLinkForm />);
  const user = userEvent.setup();
  expect(screen.queryByLabelText('Custom alias (optional)')).not.toBeInTheDocument();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByLabelText('Full short URL')).toHaveValue(created.shortUrl);
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/links', expect.objectContaining({
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ originalUrl: 'https://example.com' }),
  }));
});

it('normalizes alias and sends a Bearer while preserving case', async () => {
  fetchMock.mockResolvedValue(response(201, created));
  renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.type(screen.getByLabelText('Custom alias (optional)'), '  Promo-2026  ');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByLabelText('Full short URL')).toHaveValue(created.shortUrl);
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/links', expect.objectContaining({
    headers: expect.objectContaining({ authorization: 'Bearer signed-token' }),
    body: JSON.stringify({ originalUrl: 'https://example.com', alias: 'Promo-2026' }),
  }));
});

it('omits a blank alias while still sending the dashboard Bearer', async () => {
  fetchMock.mockResolvedValue(response(201, created));
  renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.type(screen.getByLabelText('Custom alias (optional)'), '   ');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  await screen.findByLabelText('Full short URL');
  expect(fetchMock).toHaveBeenCalledWith('/api/v1/links', expect.objectContaining({
    headers: expect.objectContaining({ authorization: 'Bearer signed-token' }),
    body: JSON.stringify({ originalUrl: 'https://example.com' }),
  }));
});

it('validates alias boundaries, characters and Java-compatible stripping', () => {
  for (const alias of ['', 'a-_', 'A'.repeat(32), 'Promo-2026_test']) expect(validAlias(alias)).toBe(true);
  for (const alias of ['ab', 'a'.repeat(33), 'bad alias', 'café', 'a/b', 'a?b', 'a#b', 'a%b', 'a.b']) expect(validAlias(alias)).toBe(false);
  expect(normalizeAlias(' \t\u2003Promo\u3000 ')).toBe('Promo');
  expect(normalizeAlias('\u00a0Promo\u00a0')).toBe('\u00a0Promo\u00a0');
});

it('focuses an invalid alias and prevents the HTTP request', async () => {
  renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.type(screen.getByLabelText('Custom alias (optional)'), 'bad alias');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveFocus();
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveAttribute('aria-invalid', 'true');
  expect(screen.getByRole('alert')).toHaveTextContent('3–32');
  expect(fetchMock).not.toHaveBeenCalled();
});

it.each([[400, 'INVALID_ALIAS'], [409, 'ALIAS_ALREADY_TAKEN']])('preserves input and focuses alias for HTTP %i', async (status, code) => {
  fetchMock.mockResolvedValue(response(status, { code, field: 'alias' }));
  renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.type(screen.getByLabelText('Custom alias (optional)'), 'Promo');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(status === 409 ? 'already taken' : '3–32');
  expect(screen.getByLabelText('URL to shorten')).toHaveValue('https://example.com');
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveValue('Promo');
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveFocus();
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveAttribute('aria-describedby', 'alias-help form-error');
});

it('prevents duplicate submissions and aborts pending requests on unmount', async () => {
  fetchMock.mockReturnValue(new Promise(() => {}));
  const { unmount } = renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  const button = screen.getByRole('button', { name: 'Shorten URL' });
  const form = button.closest('form');
  expect(form).not.toBeNull();
  if (!form) return;
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button', { name: 'Creating your link…' })).toBeDisabled();
  const signal: AbortSignal = fetchMock.mock.calls[0][1].signal;
  expect(signal.aborted).toBe(false);
  unmount();
  expect(signal.aborted).toBe(true);
});

it('keeps input and allows retry after a service failure', async () => {
  fetchMock.mockResolvedValueOnce(response(503, { code: 'SERVICE_UNAVAILABLE', field: null }))
    .mockResolvedValueOnce(response(201, created));
  renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.type(screen.getByLabelText('Custom alias (optional)'), 'Promo-2026');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('try again later');
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveValue('Promo-2026');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByLabelText('Full short URL')).toHaveValue(created.shortUrl);
});

it('ignores a response arriving after unmount', async () => {
  let complete: ((value: ReturnType<typeof response>) => void) | undefined;
  fetchMock.mockReturnValue(new Promise(resolve => { complete = resolve; }));

  const { unmount } = renderAuthenticated();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  unmount();
  await act(async () => { complete?.(response(401, { code: 'UNAUTHORIZED' })); });
  expect(readSession()).not.toBeNull();
});

it('rejects an unexpected success body without displaying a saved result', async () => {
  fetchMock.mockResolvedValue(response(201, { shortCode: 'Promo' }));
  render(<CreateLinkForm />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('URL to shorten'), 'https://example.com');
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('unexpected response');
  await waitFor(() => expect(screen.getByRole('button', { name: 'Shorten URL' })).toBeEnabled());
  expect(screen.queryByLabelText('Full short URL')).not.toBeInTheDocument();
});

function renderAuthenticated() {
  saveSession({ accessToken: 'signed-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  return render(<CreateLinkForm authenticated />);
}