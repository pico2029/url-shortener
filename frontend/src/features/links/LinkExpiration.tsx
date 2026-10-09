import { browserTimeZone } from './expiration';

export function LinkExpiration({ expiresAt, locale = 'en' }: { expiresAt: string | null; locale?: 'en' | 'it' }) {
  return <p className="field-help link-expiration">
    {expiresAt === null ? (locale === 'it' ? 'Nessuna scadenza' : 'No expiration') : <>
      {locale === 'it' ? 'Scade: ' : 'Expires: '}
      <time dateTime={expiresAt}>{new Intl.DateTimeFormat(locale === 'it' ? 'it-IT' : 'en-GB', {
        dateStyle: 'medium', timeStyle: 'short',
      }).format(new Date(expiresAt))}</time> ({browserTimeZone()})
    </>}
  </p>;
}
