import { useEffect, useRef, useState, type FormEvent } from 'react';
import { createLink, LinkApiError, type CreatedLink } from './linkApi';

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

export function CreateLinkForm() {
  const [originalUrl, setOriginalUrl] = useState('');
  const [submission, setSubmission] = useState<Submission>({ status: 'idle' });
  const pending = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => () => pending.current?.abort(), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const value = originalUrl.trim();
    if (!validUrl(value)) {
      setSubmission({ status: 'error', message: 'Enter a complete http:// or https:// URL, up to 8,192 characters.', fieldError: true });
      input.current?.focus();
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setSubmission({ status: 'loading' });
    try {
      const link = await createLink(value, controller.signal);
      if (!controller.signal.aborted) setSubmission({ status: 'success', link });
    } catch (error: unknown) {
      if (!controller.signal.aborted) {
        const fieldError = error instanceof LinkApiError && error.field === 'originalUrl';
        setSubmission({ status: 'error', fieldError,
          message: error instanceof LinkApiError ? error.message : 'The service could not be reached. Please try again.' });
        if (fieldError) input.current?.focus();
      }
    } finally {
      if (pending.current === controller) pending.current = null;
    }
  }

  const loading = submission.status === 'loading';
  const invalid = submission.status === 'error' && submission.fieldError;

  return (
    <div className="link-workspace">
      <form onSubmit={submit} noValidate aria-busy={loading} aria-describedby="availability-note">
        <label htmlFor="original-url">URL to shorten</label>
        <p id="url-help" className="field-help">Include http:// or https://.</p>
        <div className="form-row">
          <input ref={input} id="original-url" name="originalUrl" type="url" inputMode="url"
            autoComplete="url" spellCheck={false} required value={originalUrl} disabled={loading}
            aria-invalid={invalid} aria-describedby={invalid ? 'url-help form-error' : 'url-help'}
            placeholder="https://example.com/a-long-address"
            onChange={(event) => {
              setOriginalUrl(event.target.value);
              if (submission.status !== 'idle') setSubmission({ status: 'idle' });
            }} />
          <button type="submit" disabled={loading}>{loading ? 'Creating your link…' : 'Shorten URL'}</button>
        </div>
        {submission.status === 'error' && <p id="form-error" className="error" role="alert">{submission.message}</p>}
        <p className="progress" role="status">{loading ? 'Saving your link.' : ''}</p>
      </form>
      <section className="result" aria-labelledby="result-title" aria-describedby="availability-note">
        <h2 id="result-title">{submission.status === 'success' ? 'Your short link' : 'Your result'}</h2>
        <div aria-live="polite" aria-atomic="true">
          {submission.status === 'success' ? (
            <div className="result-content">
              <label htmlFor="short-url">Full short URL</label>
              <input id="short-url" className="short-url" readOnly value={submission.link.shortUrl}
                onFocus={(event) => event.currentTarget.select()} />
              <p className="field-help">Select the address to copy it manually.</p>
              <div className="destination">
                <h3>Destination</h3>
                <p>{submission.link.originalUrl}</p>
              </div>
              <p className="saved-status">Link saved successfully.</p>
            </div>
          ) : (
            <div className="empty-result">
              <p>{loading ? 'Your short link is on its way.' : 'Your short link will appear here.'}</p>
              <p className="field-help">{loading ? 'Please wait while we save your URL.' : 'Enter a URL, then select Shorten URL.'}</p>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
