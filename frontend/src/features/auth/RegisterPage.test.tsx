import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { navigate } from '../../shared/navigation';
import { RegisterPage } from './RegisterPage';

vi.mock('../../shared/navigation', () => ({ navigate: vi.fn() }));
const session = { accessToken: 'signed-token', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString() };
const fetchMock = vi.fn();
beforeEach(() => { fetchMock.mockReset(); vi.mocked(navigate).mockClear(); sessionStorage.clear(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => vi.unstubAllGlobals());

async function fill(email = 'user@example.com', password = 'una password lunga') {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.type(screen.getByLabelText('Confirm password'), password);
  return user;
}

function response(status: number, body: unknown) {
  return { ok: status < 400, status, json: async () => body };
}

describe('registrazione', () => {
  it('non invia password differenti e mostra la navigazione ad Log in', async () => {
    render(<RegisterPage />);
    const user = await fill();
    await user.clear(screen.getByLabelText('Confirm password'));
    await user.type(screen.getByLabelText('Confirm password'), 'una password diversa');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords must match.');
    expect(screen.getByLabelText('Confirm password')).toHaveFocus();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
  });

  it('distingue un account creato da un errore di accesso automatico', async () => {
    fetchMock.mockResolvedValueOnce(response(201, {})).mockRejectedValueOnce(new TypeError('network'));
    render(<RegisterPage />);
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Account created.');
    expect(navigate).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled();
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });
  it('mostra errori accessibili senza inviare dati non validi', async () => {
    render(<RegisterPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByLabelText('Email')).toHaveFocus();
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getAllByRole('alert')).toHaveLength(3);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('normalizza email, preserva password e accede dopo la registrazione', async () => {
    fetchMock.mockResolvedValueOnce(response(201, { id: 'id', email: 'user@example.com', createdAt: 'now' }))
      .mockResolvedValueOnce(response(200, session));
    render(<RegisterPage />);
    const user = await fill('USER@EXAMPLE.COM', '  una password lunga  ');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/dashboard', true));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/register', expect.objectContaining({
      body: JSON.stringify({ email: 'user@example.com', password: '  una password lunga  ' }),
    }));
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.getByLabelText('Confirm password')).toHaveValue('');
    expect(JSON.parse(sessionStorage.getItem('shortener.session')!)).toMatchObject(session);
  });

  it('impedisce invii ripetuti mentre la richiesta è in corso', async () => {
    let resolve!: (value: unknown) => void;
    fetchMock.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<RegisterPage />);
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByRole('button', { name: 'Creating your account…' })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText('Email').closest('form')!);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    fetchMock.mockResolvedValueOnce(response(200, session));
    resolve(response(201, {}));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/dashboard', true));
  });

  it('mostra il duplicato mantenendo email e password', async () => {
    fetchMock.mockResolvedValue(response(409, { code: 'EMAIL_ALREADY_REGISTERED', detail: 'This email is already registered.' }));
    render(<RegisterPage />);
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This email is already registered.');
    expect(screen.getByLabelText('Email')).toHaveValue('user@example.com');
    expect(screen.getByLabelText('Email')).toHaveFocus();
    expect(screen.getByLabelText('Password')).toHaveValue('una password lunga');
  });

  it('associa gli errori del backend ai campi', async () => {
    fetchMock.mockResolvedValue(response(400, { code: 'VALIDATION_ERROR', fieldErrors: { email: 'Invalid email address.' } }));
    render(<RegisterPage />);
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email address.');
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-describedby', 'email-error');
  });

  it.each(['network', 'server', 'html'])('consente di riprovare dopo un errore %s', async kind => {
    if (kind === 'network') fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    else if (kind === 'html') fetchMock.mockResolvedValueOnce({ ok: false, status: 502, json: async () => { throw new SyntaxError(); } });
    else fetchMock.mockResolvedValueOnce(response(500, {}));
    fetchMock.mockResolvedValueOnce(response(201, {})).mockResolvedValueOnce(response(200, session));
    render(<RegisterPage />);
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The service is unavailable.');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled());
    expect(screen.getByLabelText('Email')).toHaveValue('user@example.com');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/dashboard', true));
  });
});
