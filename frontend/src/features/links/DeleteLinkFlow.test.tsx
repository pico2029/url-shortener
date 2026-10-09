import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { saveSession } from '../auth/session';

const fetchMock = vi.fn();
const link = { originalUrl: 'https://example.com/delete', shortCode: 'Ab123456', shortUrl: 'http://localhost:8080/r/Ab123456', expiresAt: null, createdAt: '2026-10-08T10:00:00Z', status: 'SAVED' };
const page = { items: [link], page: 0, size: 20, totalElements: 1, totalPages: 1 };
const empty = { ...page, items: [], totalElements: 0, totalPages: 0 };
const response = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  saveSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });
  window.history.replaceState(null, '', '/dashboard');
});
afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear(); });

it('confirms inline, cancels without DELETE and handles authenticated 204 without parsing JSON', async () => {
  const user = userEvent.setup();
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
  render(<App />);
  await screen.findByText('Saved');
  await user.click(screen.getByRole('button', { name: 'Delete link' }));
  expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
  await user.keyboard('{Enter}');
  expect(screen.getByRole('button', { name: 'Delete link' })).toHaveFocus();
  expect(fetchMock).toHaveBeenCalledTimes(2);
  await user.click(screen.getByRole('button', { name: 'Delete link' }));
  let complete = (_value: unknown) => {};
  const json = vi.fn().mockRejectedValue(new Error('Empty body'));
  fetchMock.mockImplementationOnce(() => new Promise(resolve => { complete = resolve; })).mockResolvedValueOnce(response(empty));
  await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
  expect(screen.getByRole('button', { name: 'Deleting…' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Shorten URL' })).toBeDisabled();
  await act(async () => complete({ ok: true, status: 204, json }));
  await screen.findByText('You have no personal links yet. Create one using the form above.');
  expect(json).not.toHaveBeenCalled();
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'DELETE')).toHaveLength(1);
  expect(fetchMock).toHaveBeenCalledWith(`/api/v1/links/${link.shortCode}`, expect.objectContaining({ method: 'DELETE', headers: expect.objectContaining({ authorization: 'Bearer token' }) }));
  expect(screen.getByText('Link deleted.')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Your links' })).toHaveFocus();
});

it('refreshes the list and returns to the last valid page after deletion', async () => {
  const user = userEvent.setup();
  const first = { ...page, totalElements: 21, totalPages: 2 };
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(first)).mockResolvedValueOnce(response({ ...first, page: 1 }));
  render(<App />);
  await screen.findByText('Page 1 of 2');
  await user.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('Page 2 of 2');
  fetchMock.mockResolvedValueOnce(response(null, 204))
    .mockResolvedValueOnce(response({ ...page, items: [], page: 1, totalElements: 20 }))
    .mockResolvedValueOnce(response({ ...page, items: [{ ...link, shortCode: 'Other123', shortUrl: 'http://localhost:8080/r/Other123', expiresAt: null }], totalElements: 20 }));
  await user.click(screen.getByRole('button', { name: 'Delete link' }));
  await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
  await waitFor(() => expect(screen.getByLabelText('Short URL')).toHaveValue('http://localhost:8080/r/Other123'));
  expect(fetchMock.mock.calls.at(-2)?.[0]).toBe('/api/v1/links?page=1&size=20');
  expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/v1/links?page=0&size=20');
  expect(screen.getByRole('heading', { name: 'Your links' })).toHaveFocus();
});

it('distinguishes deletion failure from refresh failure and allows retry', async () => {
  const user = userEvent.setup();
  fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response(page));
  render(<App />);
  await screen.findByText('Saved');
  await user.click(screen.getByRole('button', { name: 'Delete link' }));
  fetchMock.mockResolvedValueOnce(response({ code: 'SERVICE_UNAVAILABLE' }, 503));
  await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('The link could not be deleted.');
  expect(screen.getByLabelText('Short URL')).toHaveValue(link.shortUrl);
  fetchMock.mockResolvedValueOnce(response(null, 204)).mockRejectedValueOnce(new TypeError('Offline'));
  await user.click(screen.getByRole('button', { name: 'Delete permanently' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Link deleted. The list could not be refreshed.');
  fetchMock.mockResolvedValueOnce(response(empty));
  await user.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByText('You have no personal links yet. Create one using the form above.');
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === 'DELETE')).toHaveLength(2);
});
