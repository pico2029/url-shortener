import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import { CreateLinkForm } from '../features/links/CreateLinkForm';

const root = document.getElementById('root');
if (!root) throw new Error('Missing application root');

createRoot(root).render(
  <StrictMode>
    <header className="site-header">
      <div className="page-width">URL Shortener</div>
    </header>
    <main>
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
  </StrictMode>,
);
