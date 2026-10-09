import { useEffect, useState } from 'react';
import { authenticatedJson, endSession, isCancelled } from '../auth/authenticatedApi';
import { readSession, sessionIdentity } from '../auth/session';
import { CreateLinkForm } from './CreateLinkForm';
import { listLinks, type LinkPage } from './linkApi';

type Listing = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: LinkPage };

export function DashboardPage() {
  const [access, setAccess] = useState<'checking' | 'ready' | 'error'>('checking');
  const [verificationAttempt, setVerificationAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [listing, setListing] = useState<Listing>({ status: 'loading' });
  const [created, setCreated] = useState(false);

  useEffect(() => {
    const session = readSession();
    if (!session) { endSession('expired'); return; }
    const identity = sessionIdentity();
    const controller = new AbortController();
    const expiry = window.setTimeout(() => {
      if (sessionIdentity() === identity) endSession('expired');
    }, Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    setAccess('checking');
    authenticatedJson('/api/v1/auth/me', { signal: controller.signal }).then(() => {
      if (!controller.signal.aborted) setAccess('ready');
    }).catch(error => {
      if (!controller.signal.aborted && !isCancelled(error)) setAccess('error');
    });
    return () => { controller.abort(); window.clearTimeout(expiry); };
  }, [verificationAttempt]);

  useEffect(() => {
    if (access !== 'ready') return;
    const controller = new AbortController();
    setListing({ status: 'loading' });
    listLinks(page, controller.signal).then(data => {
      if (!controller.signal.aborted) {
        setListing({ status: 'ready', data });
        setCreated(false);
      }
    }).catch(error => {
      if (!controller.signal.aborted && !isCancelled(error)) setListing({ status: 'error' });
    });
    return () => controller.abort();
  }, [access, page, refresh]);

  return <main lang="en" className="dashboard">
    <header className="dashboard-heading">
      <h1>Dashboard</h1>
      <button type="button" onClick={() => endSession()}>Log out</button>
    </header>
    {access === 'checking' && <p role="status">Checking your session…</p>}
    {access === 'error' && <div>
      <p role="alert">Your session could not be verified.</p>
      <button onClick={() => setVerificationAttempt(value => value + 1)}>Try again</button>
    </div>}
    {access === 'ready' && <>
      <section aria-labelledby="create-title">
        <h2 id="create-title">Create a personal link</h2>
          <CreateLinkForm locale="en" authenticated onCreated={() => {
          setCreated(true);
          setPage(0);
          setRefresh(value => value + 1);
        }} />
      </section>
      <section className="personal-links" aria-labelledby="links-title" aria-busy={listing.status === 'loading'}>
        <h2 id="links-title">Your links</h2>
        {listing.status === 'loading' && <p role="status">Loading your links…</p>}
        {listing.status === 'error' && <div>
          <p role="alert">{created ? 'Your link was created, but the list could not be refreshed.' : 'Your links could not be loaded.'}</p>
          <button onClick={() => setRefresh(value => value + 1)}>Try again</button>
        </div>}
        {listing.status === 'ready' && <>
          {listing.data.items.length === 0
            ? <p>{listing.data.totalElements === 0 ? 'You have no personal links yet. Create one using the form above.' : 'This page has no links.'}</p>
            : <ul className="link-list">
              {listing.data.items.map(link => <li key={link.shortCode}>
                <div className="link-meta">
                  <span className="saved-status">Saved</span>
                  <time dateTime={link.createdAt}>{new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.createdAt))}</time>
                </div>
                <label htmlFor={`link-${link.shortCode}`}>Short URL</label>
                <input id={`link-${link.shortCode}`} readOnly value={link.shortUrl} onFocus={event => event.currentTarget.select()} />
                <p className="link-destination"><strong>Destination</strong><br />{link.originalUrl}</p>
              </li>)}
            </ul>}
          {listing.data.totalPages > 1 && <nav className="pagination" aria-label="Link pages">
            <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
            <span>Page {page + 1} of {listing.data.totalPages}</span>
            <button disabled={page >= listing.data.totalPages - 1} onClick={() => setPage(value => value + 1)}>Next</button>
          </nav>}
        </>}
      </section>
    </>}
  </main>;
}
