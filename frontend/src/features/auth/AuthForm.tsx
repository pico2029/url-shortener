import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, postJson } from '../../shared/api';
import { navigate } from '../../shared/navigation';
import { saveSession, type Session } from './session';

type FieldErrors = { email?: string; password?: string; confirmPassword?: string };

export function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const registering = mode === 'register';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const [accountCreated, setAccountCreated] = useState(false);
  const submitting = useRef(false);
  const emailInput = useRef<HTMLInputElement>(null);
  const passwordInput = useRef<HTMLInputElement>(null);
  const confirmationInput = useRef<HTMLInputElement>(null);
  const feedback = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (pending) return;
    if (errors.email) emailInput.current?.focus();
    else if (errors.password) passwordInput.current?.focus();
    else if (errors.confirmPassword) confirmationInput.current?.focus();
    else if (message) feedback.current?.focus();
  }, [pending, errors, message]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || accountCreated) return;
    const normalizedEmail = email.trim().toLowerCase();
    const next: FieldErrors = {};
    if (!normalizedEmail || emailInput.current?.validity.typeMismatch) {
      next.email = 'Enter a valid email address.';
    } else if (normalizedEmail.length > 254) {
      next.email = 'Your email address must not exceed 254 characters.';
    }
    if (registering && (password.length < 15 || password.length > 128)) {
      next.password = 'Your password must contain 15 to 128 characters.';
    } else if (!registering && (!password || password.length > 128)) {
      next.password = 'Enter a valid password.';
    }
    if (registering && (!confirmation || confirmation !== password)) {
      next.confirmPassword = 'Passwords must match.';
    }
    setErrors(next);
    setMessage('');
    if (Object.keys(next).length) return;
    submitting.current = true;
    setPending(true);
    let created = false;
    try {
      if (registering) {
        await postJson('/api/v1/auth/register', { email: normalizedEmail, password });
        created = true;
      }
      const session = await postJson<Session>('/api/v1/auth/login', { email: normalizedEmail, password });
      saveSession(session);
      setPassword('');
      setConfirmation('');
      navigate('/dashboard', true);
    } catch (error) {
      if (created) {
        setAccountCreated(true);
        setPassword('');
        setConfirmation('');
        setMessage('Account created. Automatic login could not be completed. Go to Log in and try again.');
      } else if (error instanceof ApiError && error.problem.code === 'EMAIL_ALREADY_REGISTERED') {
        setErrors({ email: error.message });
      } else if (error instanceof ApiError && error.problem.code === 'INVALID_CREDENTIALS') {
        setMessage('Incorrect email or password. Check your details and try again.');
      } else if (error instanceof ApiError && error.problem.fieldErrors) {
        setErrors(error.problem.fieldErrors);
      } else {
        setMessage('The service is unavailable. Check your connection and try again.');
      }
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }

  return (
    <main className="registration" lang="en">
      <a className="back-link" href="/">Back to home</a>
      <nav className="auth-navigation" aria-label="Account">
        <a href="/login" aria-current={!registering ? 'page' : undefined}>Log in</a>
        <a href="/register" aria-current={registering ? 'page' : undefined}>Register</a>
      </nav>
      {!registering && new URLSearchParams(window.location.search).get('reason') === 'expired'
        && <p role="status">Your session has expired or is no longer valid. Please log in again.</p>}
      <h1>{registering ? 'Create an account' : 'Log in'}</h1>
      <p className="intro">{registering ? 'Enter your details and confirm your password to create an account.' : 'Enter your email and password to log in.'}</p>
      <form noValidate onSubmit={submit} aria-busy={pending}>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input ref={emailInput} id="email" name="email" type="email" autoComplete="email" required
            value={email} disabled={pending || accountCreated} aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'email-error' : undefined}
            onChange={event => { setEmail(event.target.value); setErrors(current => ({ ...current, email: undefined })); }} />
          {errors.email && <p className="field-error" id="email-error" role="alert">{errors.email}</p>}
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input ref={passwordInput} id="password" name="password" type="password" autoComplete={registering ? 'new-password' : 'current-password'} required
            value={password} disabled={pending || accountCreated} aria-invalid={Boolean(errors.password)}
            aria-describedby={[registering ? 'password-help' : '', errors.password ? 'password-error' : ''].filter(Boolean).join(' ') || undefined}
            onChange={event => { setPassword(event.target.value); setErrors(current => ({ ...current, password: undefined, confirmPassword: undefined })); }} />
          {registering && <p className="field-help" id="password-help">Use 15 to 128 characters. Spaces and symbols are allowed; no special combinations are required.</p>}
          {errors.password && <p className="field-error" id="password-error" role="alert">{errors.password}</p>}
        </div>
        {registering && <div className="field">
          <label htmlFor="confirm-password">Confirm password</label>
          <input ref={confirmationInput} id="confirm-password" name="confirmPassword" type="password" autoComplete="new-password" required
            value={confirmation} disabled={pending || accountCreated} aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? 'confirmation-error' : undefined}
            onChange={event => { setConfirmation(event.target.value); setErrors(current => ({ ...current, confirmPassword: undefined })); }} />
          {errors.confirmPassword && <p className="field-error" id="confirmation-error" role="alert">{errors.confirmPassword}</p>}
        </div>}
        {message && <div className="feedback error" role="alert" tabIndex={-1} ref={feedback}>{message}</div>}
        <button type="submit" disabled={pending || accountCreated}>
          {pending ? (registering ? 'Creating your account…' : 'Logging in…') : (registering ? 'Create account' : 'Log in')}
        </button>
        <p className="submission-status" role="status">{pending ? 'Please wait for the operation to complete.' : ''}</p>
      </form>
    </main>
  );
}
