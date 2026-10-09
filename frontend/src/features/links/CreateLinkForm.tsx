import { useEffect, useRef, useState, type FormEvent } from 'react';
import { createLink, LinkApiError, type CreatedLink } from './linkApi';
import { normalizeAlias, validAlias } from './alias';

type Submission =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; link: CreatedLink }
  | { status: 'error'; message: string; field?: string };

function validUrl(value: string): boolean {
  if (!value || value.length > 8192 || /\s/.test(value)) return false;
  try {
    const url = new URL(value);
    return /^https?:\/\//i.test(value) && !!url.hostname && url.port !== '0';
  } catch {
    return false;
  }
}

export function CreateLinkForm({ token, onUnauthorized }: { token?: string; onUnauthorized?: () => void }) {
  const [originalUrl, setOriginalUrl] = useState('');
  const [alias, setAlias] = useState('');
  const [submission, setSubmission] = useState<Submission>({ status: 'idle' });
  const pending = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const aliasInput = useRef<HTMLInputElement>(null);

  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => {
    if (submission.status !== 'error') return;
    if (submission.field === 'originalUrl') input.current?.focus();
    if (submission.field === 'alias') aliasInput.current?.focus();
  }, [submission]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const value = originalUrl.trim();
    if (!validUrl(value)) {
      setSubmission({ status: 'error', message: 'Enter a complete http:// or https:// URL, up to 8,192 characters.', field: 'originalUrl' });
      return;
    }
    const normalizedAlias = token ? normalizeAlias(alias) : '';
    if (!validAlias(normalizedAlias)) {
      setSubmission({ status: 'error', message: 'Use 3–32 ASCII letters, numbers, hyphens or underscores. Aliases are case-sensitive.', field: 'alias' });
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setSubmission({ status: 'loading' });
    try {
      const link = await createLink(value, controller.signal, { token, alias: normalizedAlias });
      if (!controller.signal.aborted) setSubmission({ status: 'success', link });
    } catch (error: unknown) {
      if (!controller.signal.aborted) {
        if (error instanceof LinkApiError && error.status === 401 && onUnauthorized) {
          onUnauthorized();
          return;
        }
        const field = error instanceof LinkApiError ? error.field : undefined;
        setSubmission({ status: 'error', field,
          message: error instanceof LinkApiError ? error.message : 'The service could not be reached. Please try again.' });
      }
    } finally {
      if (pending.current === controller) pending.current = null;
    }
  }

  const loading = submission.status === 'loading';
  const invalid = submission.status === 'error' && submission.field === 'originalUrl';
  const invalidAlias = submission.status === 'error' && submission.field === 'alias';

  return (
    <div className="link-workspace">
      <form onSubmit={submit} noValidate aria-busy={loading}>
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
          {token && <div>
            <label htmlFor="custom-alias">Custom alias (optional)</label>
            <p id="alias-help" className="field-help">Use 3–32 ASCII letters, numbers, hyphens or underscores. Aliases are case-sensitive. Leave blank to generate a code automatically.</p>
            <input ref={aliasInput} id="custom-alias" name="alias" type="text" autoComplete="off"
              spellCheck={false} autoCapitalize="none" value={alias} disabled={loading}
              aria-invalid={invalidAlias} aria-describedby={invalidAlias ? 'alias-help form-error' : 'alias-help'}
              placeholder="Promo-2026" onChange={(event) => {
                setAlias(event.target.value);
                if (submission.status !== 'idle') setSubmission({ status: 'idle' });
              }} />
          </div>}
          <button type="submit" disabled={loading}>{loading ? 'Creating your link…' : 'Shorten URL'}</button>
        </div>
        {submission.status === 'error' && <p id="form-error" className="error" role="alert">{submission.message}</p>}
        <p className="progress" role="status">{loading ? 'Saving your link.' : ''}</p>
      </form>
      <section className="result" aria-labelledby="result-title">
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
