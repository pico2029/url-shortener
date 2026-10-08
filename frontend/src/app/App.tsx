import { useEffect, useSyncExternalStore } from 'react';
import { CreateLinkForm } from '../features/links/CreateLinkForm';
import { AuthForm } from '../features/auth/AuthForm';
import { DashboardPage } from '../features/links/DashboardPage';

function subscribeNavigation(update: () => void) {
  window.addEventListener('popstate', update);
  return () => window.removeEventListener('popstate', update);
}

export function App() {
  const path = useSyncExternalStore(subscribeNavigation, () => window.location.pathname);
  useEffect(() => {
    document.title = `${path === '/dashboard' ? 'Dashboard' : path === '/login' ? 'Accedi' : path === '/register' ? 'Registrati' : 'Home'} - URL Shortener`;
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
