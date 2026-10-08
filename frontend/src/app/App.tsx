import { useEffect, useState, useSyncExternalStore } from 'react';
import { CreateLinkForm } from '../features/links/CreateLinkForm';
import { AuthForm } from '../features/auth/AuthForm';
import { clearSession, readSession } from '../features/auth/session';
import { ApiError, getJson } from '../shared/api';
import { navigate } from '../shared/navigation';

function DashboardPage() {
  const [status, setStatus] = useState<'checking' | 'ready' | 'error'>('checking');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    const session = readSession();
    if (!session) {
      navigate('/login', true);
      return;
    }
    const expiry = window.setTimeout(() => {
      clearSession();
      navigate('/login', true);
    }, Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    setStatus('checking');
    getJson('/api/v1/auth/me', session.accessToken).then(() => {
      if (active) setStatus('ready');
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiError && error.problem.status === 401) {
        clearSession();
        navigate('/login', true);
      } else setStatus('error');
    });
    return () => { active = false; window.clearTimeout(expiry); };
  }, [attempt]);

  return <main>
    <h1>Dashboard</h1>
    {status === 'checking' && <p role="status">Verifica dell’accesso…</p>}
    {status === 'error' && <>
      <p role="alert">Non è stato possibile verificare l’accesso.</p>
      <button onClick={() => setAttempt(value => value + 1)}>Riprova</button>
    </>}
  </main>;
}

function subscribeNavigation(update: () => void) {
  window.addEventListener('popstate', update);
  return () => window.removeEventListener('popstate', update);
}

export function App() {
  const path = useSyncExternalStore(subscribeNavigation, () => window.location.pathname);
  useEffect(() => {
    document.title = `${path === '/dashboard' ? 'Dashboard' : path === '/login' ? 'Accedi' : path === '/register' ? 'Registrati' : 'Home'} — URL Shortener`;
  }, [path]);

  if (path === '/register') return <AuthForm key="register" mode="register" />;
  if (path === '/login') return <AuthForm key="login" mode="login" />;
  if (path === '/dashboard') return <DashboardPage />;
  return <>
    <header className="site-header"><div className="page-width">URL Shortener</div></header>
    <main>
      <nav className="auth-navigation" aria-label="Account" lang="it">
        <a href="/login">Accedi</a>
        <a href="/register">Crea un account</a>
      </nav>
      <header className="page-intro">
        <h1>Shorten a URL</h1>
        <p className="intro">Create a short link. No account needed.</p>
      </header>
      <aside className="availability-notice" id="availability-note" aria-label="Service availability">
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v6m0 3v.1" />
        </svg>
        <p>Links are saved, but redirects are not available yet.</p>
      </aside>
      <CreateLinkForm />
    </main>
  </>;
}
