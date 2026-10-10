import { AppLink } from '../shared/AppLink';
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

  return <>
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="site-header">
      <div className="page-width header-content">
        <AppLink className="brand" href="/" aria-label="URL Shortener home">
          <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M13 21l-2 2a6 6 0 0 1-8-8l7-7a6 6 0 0 1 8 0M19 11l2-2a6 6 0 0 1 8 8l-7 7a6 6 0 0 1-8 0M11 21l10-10" /></svg>
          <span>URL Shortener</span>
        </AppLink>
        <nav className="header-navigation" aria-label="Main navigation">
          {path === '/' ? <><AppLink href="/login">Log in</AppLink><AppLink className="header-action" href="/register">Create an account</AppLink></>
            : <AppLink href="/">Shorten a URL</AppLink>}
        </nav>
      </div>
    </header>
    {path === '/register' ? <AuthForm key="register" mode="register" />
      : path === '/login' ? <AuthForm key="login" mode="login" />
      : path === '/dashboard' ? <DashboardPage />
      : <main id="main-content" className="home-page" tabIndex={-1}>
        <header className="page-intro">
          <h1>Shorten a URL</h1>
          <p className="intro">Create a short link. No account needed.</p>
        </header>
        <CreateLinkForm />
        <section className="home-guide" aria-label="About your links">
          <div><h2>A shorter way to share.</h2><p>Paste a destination, create your link and copy the full address. Your original URL stays intact.</p></div>
          <div><h2>Make it yours.</h2><p>With an account, choose an alias, set an expiration and keep your links together.</p><AppLink href="/register">Register</AppLink></div>
        </section>
      </main>}
    <footer className="site-footer page-width"><span>URL Shortener</span><span>Less address. Same destination.</span></footer>
  </>;
}
