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
      next.email = 'Inserisci un indirizzo email valido.';
    } else if (normalizedEmail.length > 254) {
      next.email = "L'email può contenere al massimo 254 caratteri.";
    }
    if (registering && (password.length < 15 || password.length > 128)) {
      next.password = 'La password deve contenere da 15 a 128 caratteri.';
    } else if (!registering && (!password || password.length > 128)) {
      next.password = 'Inserisci una password valida.';
    }
    if (registering && (!confirmation || confirmation !== password)) {
      next.confirmPassword = 'Le password devono coincidere.';
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
        setMessage('Account creato. Non è stato possibile completare l’accesso automatico: passa ad Accedi e riprova.');
      } else if (error instanceof ApiError && error.problem.code === 'EMAIL_ALREADY_REGISTERED') {
        setErrors({ email: error.message });
      } else if (error instanceof ApiError && error.problem.code === 'INVALID_CREDENTIALS') {
        setMessage('Email o password non corrette. Controlla i dati e riprova.');
      } else if (error instanceof ApiError && error.problem.fieldErrors) {
        setErrors(error.problem.fieldErrors);
      } else {
        setMessage('Il servizio non è disponibile. Controlla la connessione e riprova.');
      }
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }

  return (
    <main className="registration" lang="it">
      <a className="back-link" href="/">Torna alla home</a>
      <nav className="auth-navigation" aria-label="Account">
        <a href="/login" aria-current={!registering ? 'page' : undefined}>Accedi</a>
        <a href="/register" aria-current={registering ? 'page' : undefined}>Registrati</a>
      </nav>
      <h1>{registering ? 'Crea un account' : 'Accedi'}</h1>
      <p className="intro">{registering ? 'Inserisci i tuoi dati e conferma la password per registrarti.' : 'Inserisci email e password per entrare nel tuo account.'}</p>
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
          {registering && <p className="field-help" id="password-help">Da 15 a 128 caratteri. Puoi usare spazi e simboli; non servono combinazioni particolari.</p>}
          {errors.password && <p className="field-error" id="password-error" role="alert">{errors.password}</p>}
        </div>
        {registering && <div className="field">
          <label htmlFor="confirm-password">Conferma password</label>
          <input ref={confirmationInput} id="confirm-password" name="confirmPassword" type="password" autoComplete="new-password" required
            value={confirmation} disabled={pending || accountCreated} aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={errors.confirmPassword ? 'confirmation-error' : undefined}
            onChange={event => { setConfirmation(event.target.value); setErrors(current => ({ ...current, confirmPassword: undefined })); }} />
          {errors.confirmPassword && <p className="field-error" id="confirmation-error" role="alert">{errors.confirmPassword}</p>}
        </div>}
        {message && <div className="feedback error" role="alert" tabIndex={-1} ref={feedback}>{message}</div>}
        <button type="submit" disabled={pending || accountCreated}>
          {pending ? (registering ? 'Registrazione in corso…' : 'Accesso in corso…') : (registering ? 'Crea account' : 'Accedi')}
        </button>
        <p className="submission-status" role="status">{pending ? 'Attendi il completamento dell’operazione.' : ''}</p>
      </form>
    </main>
  );
}
