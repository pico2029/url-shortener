import { useEffect, useRef, useState, type FormEvent } from 'react';
import { isCancelled } from '../auth/authenticatedApi';
import { createLink, LinkApiError, type CreatedLink } from './linkApi';
import { CopyLinkButton } from './CopyLinkButton';

type Submission =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; link: CreatedLink }
  | { status: 'error'; message: string; fieldError: boolean };

function validUrl(value: string): boolean {
  if (!value || value.length > 8192 || /\s/.test(value)) return false;
  try {
    const url = new URL(value);
    return /^https?:\/\//i.test(value) && !!url.hostname && url.port !== '0';
  } catch {
    return false;
  }
}

export function CreateLinkForm({ locale = 'en', authenticated = false, disabled = false, onBusyChange, onCreated }: {
  locale?: 'en' | 'it'; authenticated?: boolean; disabled?: boolean; onBusyChange?: (busy: boolean) => void; onCreated?: () => void;
}) {
  const t = (english: string, italian: string) => locale === 'it' ? italian : english;
  const [originalUrl, setOriginalUrl] = useState('');
  const [submission, setSubmission] = useState<Submission>({ status: 'idle' });
  const pending = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => () => pending.current?.abort(), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || disabled) return;
    const value = originalUrl.trim();
    if (!validUrl(value)) {
      setSubmission({ status: 'error', message: t('Enter a complete http:// or https:// URL, up to 8,192 characters.', 'Inserisci un URL HTTP/HTTPS completo, entro 8.192 caratteri.'), fieldError: true });
      input.current?.focus();
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    onBusyChange?.(true);
    setSubmission({ status: 'loading' });
    try {
      const link = await createLink(value, controller.signal, authenticated, locale);
      if (!controller.signal.aborted) {
        setSubmission({ status: 'success', link });
        onCreated?.();
      }
    } catch (error: unknown) {
      if (!controller.signal.aborted && !isCancelled(error)) {
        const fieldError = error instanceof LinkApiError && error.field === 'originalUrl';
        setSubmission({ status: 'error', fieldError,
          message: error instanceof LinkApiError ? error.message : t('The service could not be reached. Please try again.', 'Il servizio non è raggiungibile. Riprova.') });
        if (fieldError) input.current?.focus();
      }
    } finally {
      if (pending.current === controller) {
        pending.current = null;
        onBusyChange?.(false);
      }
    }
  }

  const loading = submission.status === 'loading';
  const invalid = submission.status === 'error' && submission.fieldError;

  return (
    <div className="link-workspace">
      <form onSubmit={submit} noValidate aria-busy={loading}>
        <label htmlFor="original-url">{t('URL to shorten', 'URL da accorciare')}</label>
        <p id="url-help" className="field-help">{t('Include http:// or https://.', 'Includi http:// oppure https://.')}</p>
        <div className="form-row">
          <input ref={input} id="original-url" name="originalUrl" type="url" inputMode="url"
            autoComplete="url" spellCheck={false} required value={originalUrl} disabled={loading || disabled}
            aria-invalid={invalid} aria-describedby={invalid ? 'url-help form-error' : 'url-help'}
            placeholder="https://example.com/a-long-address"
            onChange={(event) => {
              setOriginalUrl(event.target.value);
              if (submission.status !== 'idle') setSubmission({ status: 'idle' });
            }} />
          <button type="submit" disabled={loading || disabled}>{loading ? t('Creating your link…', 'Creazione in corso…') : t('Shorten URL', 'Accorcia URL')}</button>
        </div>
        {submission.status === 'error' && <p id="form-error" className="error" role="alert">{submission.message}</p>}
        <p className="progress" role="status">{loading ? t('Saving your link.', 'Salvataggio del link.') : ''}</p>
      </form>
      <section className="result" aria-labelledby="result-title">
        <h2 id="result-title">{submission.status === 'success' ? t('Your short link', 'Il tuo link breve') : t('Your result', 'Risultato')}</h2>
        <div>
          {submission.status === 'success' ? (
            <div className="result-content">
              <label htmlFor="short-url">{t('Full short URL', 'URL breve completo')}</label>
              <div className="short-url-controls">
                <input id="short-url" className="short-url" readOnly value={submission.link.shortUrl}
                  onFocus={(event) => event.currentTarget.select()} />
                <CopyLinkButton shortUrl={submission.link.shortUrl} locale={locale} />
              </div>
              <p className="field-help">{t('Copy your link with the button, or select the address to copy it manually.', 'Copia il link con il pulsante oppure seleziona l’indirizzo per copiarlo manualmente.')}</p>
              <div className="destination">
                <h3>{t('Destination', 'Destinazione')}</h3>
                <p>{submission.link.originalUrl}</p>
              </div>
              <p className="saved-status" role="status">{t('Link saved successfully.', 'Link creato correttamente.')}</p>
            </div>
          ) : (
            <div className="empty-result" aria-live="polite" aria-atomic="true">
              <p>{loading ? t('Your short link is on its way.', 'Stiamo creando il tuo link breve.') : t('Your short link will appear here.', 'Il tuo link breve apparirà qui.')}</p>
              <p className="field-help">{loading ? t('Please wait while we save your URL.', 'Attendi il salvataggio dell’URL.') : t('Enter a URL, then select Shorten URL.', 'Inserisci un URL e premi Accorcia URL.')}</p>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
