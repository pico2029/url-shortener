import { ApiError, requestJson } from '../../shared/api';
import { authenticatedJson } from '../auth/authenticatedApi';
import { sessionToken } from '../auth/session';

export interface CreatedLink {
  originalUrl: string;
  shortCode: string;
  shortUrl: string;
}
export interface PersonalLink extends CreatedLink { createdAt: string; status: 'SAVED' }
export interface LinkPage { items: PersonalLink[]; page: number; size: number; totalElements: number; totalPages: number }

export class LinkApiError extends Error {
  constructor(message: string, readonly field?: string) { super(message); }
}

function isCreatedLink(value: unknown): value is CreatedLink {
  return typeof value === 'object' && value !== null
    && 'originalUrl' in value && typeof value.originalUrl === 'string'
    && 'shortCode' in value && typeof value.shortCode === 'string'
    && 'shortUrl' in value && typeof value.shortUrl === 'string';
}

function isPersonalLink(value: unknown): value is PersonalLink {
  return isCreatedLink(value) && 'createdAt' in value && typeof value.createdAt === 'string'
    && Number.isFinite(Date.parse(value.createdAt)) && 'status' in value && value.status === 'SAVED';
}

export async function listLinks(page: number, signal: AbortSignal): Promise<LinkPage> {
  const result = await authenticatedJson<unknown>(`/api/v1/links?page=${page}&size=20`, { signal });
  if (!result || typeof result !== 'object' || !('items' in result) || !Array.isArray(result.items)
      || !result.items.every(isPersonalLink)
      || !('page' in result) || typeof result.page !== 'number' || !Number.isInteger(result.page) || result.page < 0
      || !('size' in result) || typeof result.size !== 'number' || !Number.isInteger(result.size) || result.size < 1
      || !('totalElements' in result) || typeof result.totalElements !== 'number' || !Number.isInteger(result.totalElements) || result.totalElements < 0
      || !('totalPages' in result) || typeof result.totalPages !== 'number' || !Number.isInteger(result.totalPages) || result.totalPages < 0) {
    throw new Error('Invalid link page');
  }
  return { items: result.items, page: result.page, size: result.size, totalElements: result.totalElements, totalPages: result.totalPages };
}

export async function createLink(originalUrl: string, signal: AbortSignal, requireSession = false,
  locale: 'en' | 'it' = 'en', alias?: string): Promise<CreatedLink> {
  const options = {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ originalUrl, ...(alias ? { alias } : {}) }), signal,
  };
  try {
    const body: unknown = requireSession || sessionToken()
      ? await authenticatedJson('/api/v1/links', options)
      : await requestJson('/api/v1/links', options);
    if (!isCreatedLink(body)) throw new LinkApiError(locale === 'it'
      ? 'Il servizio ha restituito una risposta inattesa. Riprova più tardi.'
      : 'The service returned an unexpected response. Please try again later.');
    return body;
  } catch (error) {
    if (error instanceof ApiError) {
      const invalid = error.problem.code === 'INVALID_URL';
      let message = locale === 'it'
        ? invalid ? 'Inserisci un URL HTTP/HTTPS completo, entro 8.192 caratteri.' : 'Impossibile creare il link. Riprova più tardi.'
        : invalid ? 'Enter a complete http:// or https:// URL, up to 8,192 characters.' : 'Your link could not be created. Please try again later.';
      const invalidAlias = error.problem.code === 'INVALID_ALIAS';
      const occupiedAlias = error.problem.code === 'ALIAS_ALREADY_TAKEN';
      if (invalidAlias) message = locale === 'it'
        ? 'Usa 3–32 lettere ASCII, numeri, trattini o underscore. Maiuscole e minuscole sono distinte.'
        : 'Use 3–32 ASCII letters, numbers, hyphens or underscores. Aliases are case-sensitive.';
      if (occupiedAlias) message = locale === 'it'
        ? 'Questo alias è già occupato. Scegli un altro alias.'
        : 'This alias is already taken. Choose another alias.';
      throw new LinkApiError(message, invalid ? 'originalUrl' : invalidAlias || occupiedAlias ? 'alias' : undefined);
    }
    throw error;
  }
}
