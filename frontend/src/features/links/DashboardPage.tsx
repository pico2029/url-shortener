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

  return <main lang="it" className="dashboard">
    <header className="dashboard-heading">
      <h1>Dashboard</h1>
      <button type="button" onClick={() => endSession()}>Esci</button>
    </header>
    {access === 'checking' && <p role="status">Verifica dell’accesso…</p>}
    {access === 'error' && <div>
      <p role="alert">Non è stato possibile verificare l’accesso.</p>
      <button onClick={() => setVerificationAttempt(value => value + 1)}>Riprova</button>
    </div>}
    {access === 'ready' && <>
      <section aria-labelledby="create-title">
        <h2 id="create-title">Crea un link personale</h2>
        <p className="availability-notice" id="availability-note">I link vengono salvati nel tuo account. I redirect non sono ancora disponibili.</p>
        <CreateLinkForm locale="it" authenticated onCreated={() => {
          setCreated(true);
          setPage(0);
          setRefresh(value => value + 1);
        }} />
      </section>
      <section className="personal-links" aria-labelledby="links-title" aria-busy={listing.status === 'loading'}>
        <h2 id="links-title">I tuoi link</h2>
        {listing.status === 'loading' && <p role="status">Caricamento dei link…</p>}
        {listing.status === 'error' && <div>
          <p role="alert">{created ? 'Il link è stato creato, ma non è stato possibile aggiornare l’elenco.' : 'Non è stato possibile caricare i tuoi link.'}</p>
          <button onClick={() => setRefresh(value => value + 1)}>Riprova</button>
        </div>}
        {listing.status === 'ready' && <>
          {listing.data.items.length === 0
            ? <p>{listing.data.totalElements === 0 ? 'Non hai ancora creato link personali. Inizia dal form qui sopra.' : 'Questa pagina non contiene link.'}</p>
            : <ul className="link-list">
              {listing.data.items.map(link => <li key={link.shortCode}>
                <div className="link-meta">
                  <span className="saved-status">Salvato</span>
                  <time dateTime={link.createdAt}>{new Intl.DateTimeFormat('it-IT', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.createdAt))}</time>
                </div>
                <label htmlFor={`link-${link.shortCode}`}>URL breve</label>
                <input id={`link-${link.shortCode}`} readOnly value={link.shortUrl} onFocus={event => event.currentTarget.select()} />
                <p className="link-destination"><strong>Destinazione</strong><br />{link.originalUrl}</p>
              </li>)}
            </ul>}
          {listing.data.totalPages > 1 && <nav className="pagination" aria-label="Pagine dei link">
            <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Precedente</button>
            <span>Pagina {page + 1} di {listing.data.totalPages}</span>
            <button disabled={page >= listing.data.totalPages - 1} onClick={() => setPage(value => value + 1)}>Successiva</button>
          </nav>}
        </>}
      </section>
    </>}
  </main>;
}
