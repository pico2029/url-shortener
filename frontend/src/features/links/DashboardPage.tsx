import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api';
import { authenticatedJson, endSession, isCancelled } from '../auth/authenticatedApi';
import { readSession, sessionIdentity } from '../auth/session';
import { CreateLinkForm } from './CreateLinkForm';
import { CopyLinkButton } from './CopyLinkButton';
import { deleteLink, listLinks, type LinkPage } from './linkApi';

type Listing = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: LinkPage };

export function DashboardPage() {
  const [access, setAccess] = useState<'checking' | 'ready' | 'error'>('checking');
  const [verificationAttempt, setVerificationAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [listing, setListing] = useState<Listing>({ status: 'loading' });
  const [created, setCreated] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deletion, setDeletion] = useState<{ code: string; status: 'confirming' | 'running' | 'error' } | null>(null);
  const [deleteFeedback, setDeleteFeedback] = useState('');
  const deleting = deletion?.status === 'running';
  const pendingDelete = useRef<AbortController | null>(null);
  const deletionRefresh = useRef(false);
  const focusAfterRefresh = useRef(false);
  const linksTitle = useRef<HTMLHeadingElement>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);
  const restoreDeleteFocus = useRef(false);
  const deleteButton = useRef<HTMLButtonElement | null>(null);

  useEffect(() => () => pendingDelete.current?.abort(), []);
  useEffect(() => {
    if (deletion?.status === 'confirming') cancelButton.current?.focus();
    if (!deletion && restoreDeleteFocus.current) {
      restoreDeleteFocus.current = false;
      deleteButton.current?.focus();
    }
  }, [deletion]);
  useEffect(() => {
    if (listing.status === 'ready' && focusAfterRefresh.current) {
      focusAfterRefresh.current = false;
      linksTitle.current?.focus();
    }
  }, [listing]);

  async function confirmDelete(code: string) {
    if (pendingDelete.current) return;
    const controller = new AbortController();
    pendingDelete.current = controller;
    setDeletion({ code, status: 'running' });
    setDeleteFeedback('');
    setCreated(false);
    try {
      try {
        await deleteLink(code, controller.signal);
        if (controller.signal.aborted) return;
        setDeleteFeedback('Link deleted.');
      } catch (error: unknown) {
        if (controller.signal.aborted || isCancelled(error)) return;
        if (!(error instanceof ApiError && error.problem.status === 404)) throw error;
        setDeleteFeedback('The link is no longer available.');
      }
      deletionRefresh.current = true;
      focusAfterRefresh.current = true;
      setRefresh(value => value + 1);
    } catch (error: unknown) {
      if (!controller.signal.aborted && !isCancelled(error)) {
        setDeletion({ code, status: 'error' });
      }
      if (pendingDelete.current === controller) pendingDelete.current = null;
    }
  }

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
        if (deletionRefresh.current && page > 0 && data.items.length === 0) {
          setPage(Math.min(page - 1, Math.max(0, data.totalPages - 1)));
          return;
        }
        setListing({ status: 'ready', data });
        setCreated(false);
        if (deletionRefresh.current) {
          deletionRefresh.current = false;
          pendingDelete.current = null;
          setDeletion(null);
        }
      }
    }).catch(error => {
      if (!controller.signal.aborted && !isCancelled(error)) {
        setListing({ status: 'error' });
        if (deletionRefresh.current) {
          deletionRefresh.current = false;
          pendingDelete.current = null;
          setDeletion(null);
        }
      }
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
          <CreateLinkForm locale="en" authenticated disabled={deleting} onBusyChange={setCreating} onCreated={() => {
          setCreated(true);
          setDeleteFeedback('');
          setDeletion(null);
          setPage(0);
          setRefresh(value => value + 1);
        }} />
      </section>
      <section className="personal-links" aria-labelledby="links-title" aria-busy={listing.status === 'loading'}>
        <h2 id="links-title" ref={linksTitle} tabIndex={-1}>Your links</h2>
        <p className="delete-feedback" role="status">{deleteFeedback}</p>
        {listing.status === 'loading' && <p role="status">Loading your links…</p>}
        {listing.status === 'error' && <div>
          <p role="alert">{deleteFeedback ? `${deleteFeedback} The list could not be refreshed.` : created ? 'Your link was created, but the list could not be refreshed.' : 'Your links could not be loaded.'}</p>
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
                <div className="short-url-controls">
                  <input id={`link-${link.shortCode}`} readOnly value={link.shortUrl} onFocus={event => event.currentTarget.select()} />
                  <CopyLinkButton shortUrl={link.shortUrl} />
                </div>
                <p className="link-destination"><strong>Destination</strong><br />{link.originalUrl}</p>
                <div className="delete-action">
                  <button type="button" className="delete-button" disabled={deleting || creating}
                    hidden={deletion?.code === link.shortCode}
                    onClick={event => {
                      deleteButton.current = event.currentTarget;
                      setDeletion({ code: link.shortCode, status: 'confirming' });
                    }}>Delete link</button>
                  {deletion?.code === link.shortCode && <div>
                    <p id={`delete-confirm-${link.shortCode}`}>Delete this link permanently? It will no longer work.</p>
                    {deletion.status === 'error' && <p className="error" role="alert">The link could not be deleted. Please try again.</p>}
                    <div className="delete-controls">
                      <button type="button" className="delete-button" disabled={deleting}
                        aria-describedby={`delete-confirm-${link.shortCode}`}
                        onClick={() => confirmDelete(link.shortCode)}>{deleting ? 'Deleting…' : 'Delete permanently'}</button>
                      <button type="button" ref={cancelButton} disabled={deleting} onClick={() => {
                        restoreDeleteFocus.current = true;
                        setDeletion(null);
                      }}>Cancel</button>
                    </div>
                  </div>}
                </div>
              </li>)}
            </ul>}
          {listing.data.totalPages > 1 && <nav className="pagination" aria-label="Link pages">
            <button disabled={deleting || page === 0} onClick={() => setPage(value => value - 1)}>Previous</button>
            <span>Page {page + 1} of {listing.data.totalPages}</span>
            <button disabled={deleting || page >= listing.data.totalPages - 1} onClick={() => setPage(value => value + 1)}>Next</button>
          </nav>}
        </>}
      </section>
    </>}
  </main>;
}
