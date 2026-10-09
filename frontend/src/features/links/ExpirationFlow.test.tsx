import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { CreateLinkForm } from './CreateLinkForm';
import { App } from '../../app/App';
import { saveSession } from '../auth/session';
import { browserTimeZone, expirationInstant } from './expiration';

const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body });
const link = { originalUrl: 'https://example.com', shortCode: 'Timed', shortUrl: 'http://localhost:8080/r/Timed', expiresAt: null };
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); sessionStorage.clear(); });
afterEach(() => { vi.unstubAllGlobals(); sessionStorage.clear(); });
const session = () => saveSession({ accessToken: 'token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() });

it('reserves expiration for authenticated creation, converts local time and preserves input after errors', async () => {
  const user = userEvent.setup();
  const anonymous = render(<CreateLinkForm />);
  expect(screen.queryByLabelText('Expiration (optional)')).not.toBeInTheDocument();
  anonymous.unmount();
  session();
  render(<CreateLinkForm authenticated />);
  const expiration = screen.getByLabelText('Expiration (optional)');
  await user.type(screen.getByLabelText('URL to shorten'), link.originalUrl);
  await user.type(screen.getByLabelText('Custom alias (optional)'), 'Timed');
  fireEvent.input(expiration, { target: { value: '2035-04-05T10:30' } });
  const expected = new Date('2035-04-05T10:30').toISOString();
  fetchMock.mockResolvedValueOnce(response({ code: 'INVALID_EXPIRATION', field: 'expiresAt' }, 400));
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Choose a valid date and time in the future.');
  expect(expiration).toHaveFocus();
  expect(expiration).toHaveValue('2035-04-05T10:30');
  expect(screen.getByLabelText('Custom alias (optional)')).toHaveValue('Timed');
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ originalUrl: link.originalUrl, alias: 'Timed', expiresAt: expected });
  fetchMock.mockResolvedValueOnce(response({ ...link, expiresAt: expected }, 201));
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  const result = await screen.findByRole('region', { name: 'Your short link' });
  expect(within(result).getByText('Expires:', { exact: false })).toHaveTextContent(browserTimeZone());
  expect(result.querySelector('time')).toHaveAttribute('datetime', expected);
  fireEvent.input(expiration, { target: { value: '' } });
  fetchMock.mockResolvedValueOnce(response(link, 201));
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  await screen.findByText('No expiration');
  expect(JSON.parse(fetchMock.mock.calls.at(-1)?.[1].body)).not.toHaveProperty('expiresAt');
  fireEvent.input(expiration, { target: { value: '2000-01-01T10:30' } });
  const calls = fetchMock.mock.calls.length;
  await user.click(screen.getByRole('button', { name: 'Shorten URL' }));
  expect(fetchMock).toHaveBeenCalledTimes(calls);
  expect(expiration).toHaveFocus();
  expect(() => expirationInstant('2035-02-30T10:30')).toThrow();
});

it('shows expiration, timezone and expired status while preserving copy and deletion', async () => {
  const user = userEvent.setup();
  const writeText = vi.fn().mockResolvedValue(undefined);
  const descriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
  try {
    session();
    window.history.replaceState(null, '', '/dashboard');
    const timed = { ...link, expiresAt: '2020-01-01T10:30:00Z', status: 'EXPIRED', createdAt: '2019-01-01T10:30:00Z' };
    const forever = { ...timed, shortCode: 'Forever', shortUrl: 'http://localhost:8080/r/Forever', expiresAt: null, status: 'SAVED' };
    fetchMock.mockResolvedValueOnce(response({})).mockResolvedValueOnce(response({ items: [timed, forever], page: 0, size: 20, totalElements: 2, totalPages: 1 }));
    render(<App />);
    await screen.findByText('Expired');
    const rows = screen.getAllByRole('listitem');
    expect(within(rows[0]).getByText('Expires:', { exact: false })).toHaveTextContent(browserTimeZone());
    expect(within(rows[1]).getByText('No expiration')).toBeInTheDocument();
    await user.click(within(rows[0]).getByRole('button', { name: 'Copy link' }));
    expect(writeText).toHaveBeenCalledExactlyOnceWith(timed.shortUrl);
    await user.click(within(rows[0]).getByRole('button', { name: 'Delete link' }));
    expect(within(rows[0]).getByRole('button', { name: 'Cancel' })).toHaveFocus();
    expect(within(rows[0]).getByRole('button', { name: 'Delete permanently' })).toBeEnabled();
  } finally {
    if (descriptor) Object.defineProperty(navigator, 'clipboard', descriptor);
    else Reflect.deleteProperty(navigator, 'clipboard');
  }
});
