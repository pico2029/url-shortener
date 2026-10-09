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
    document.title = `${path === '/dashboard' ? 'Dashboard' : path === '/login' ? 'Log in' : path === '/register' ? 'Register' : 'Home'} - URL Shortener`;
  }, [path]);

  if (path === '/register') return <AuthForm key="register" mode="register" />;
  if (path === '/login') return <AuthForm key="login" mode="login" />;
  if (path === '/dashboard') return <DashboardPage />;
  return <>
    <header className="site-header"><div className="page-width">URL Shortener</div></header>
    <main>
      <nav className="auth-navigation" aria-label="Account" lang="en">
        <a href="/login">Log in</a>
        <a href="/register">Create an account</a>
      </nav>
      <header className="page-intro">
        <h1>Shorten a URL</h1>
        <p className="intro">Create a short link. No account needed.</p>
      </header>
      <CreateLinkForm />
    </main>
  </>;
}
