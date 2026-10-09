import { useEffect, useRef, useState } from 'react';

type CopyLinkProps = { shortUrl: string; locale?: 'en' | 'it' };

export function CopyLinkButton({ shortUrl, locale = 'en' }: CopyLinkProps) {
  return <CopyAction key={shortUrl} shortUrl={shortUrl} locale={locale} />;
}

function CopyAction({ shortUrl, locale }: CopyLinkProps) {
  const [status, setStatus] = useState<'idle' | 'copying' | 'success' | 'error'>('idle');
  const pending = useRef(false);
  const mounted = useRef(false);
  const t = (english: string, italian: string) => locale === 'it' ? italian : english;

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function copy() {
    if (pending.current) return;
    pending.current = true;
    setStatus('copying');
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(shortUrl);
      if (mounted.current) setStatus('success');
    } catch {
      if (mounted.current) setStatus('error');
    } finally {
      pending.current = false;
    }
  }

  return <div className="copy-action">
    <button type="button" onClick={copy} disabled={status === 'copying'}>
      {status === 'copying' ? t('Copying…', 'Copia in corso…') : t('Copy link', 'Copia link')}
    </button>
    <p className="copy-feedback" role="status" aria-atomic="true">
      {status === 'success' ? t('Link copied.', 'Link copiato.') : ''}
    </p>
    {status === 'error' && <p className="error copy-error" role="alert">
      {t('Could not copy the link. Select the address and copy it manually.', 'Impossibile copiare il link. Seleziona l’indirizzo e copialo manualmente.')}
    </p>}
  </div>;
}
