export interface CreatedLink {
  originalUrl: string;
  shortCode: string;
  shortUrl: string;
}

export class LinkApiError extends Error {
  constructor(message: string, readonly field?: string, readonly status?: number) {
    super(message);
  }
}

function isCreatedLink(value: unknown): value is CreatedLink {
  return typeof value === 'object' && value !== null
    && 'originalUrl' in value && typeof value.originalUrl === 'string'
    && 'shortCode' in value && typeof value.shortCode === 'string'
    && 'shortUrl' in value && typeof value.shortUrl === 'string';
}

export async function createLink(originalUrl: string, signal: AbortSignal,
  options: { alias?: string; token?: string } = {}): Promise<CreatedLink> {
  const response = await fetch('/api/v1/links', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}) },
    body: JSON.stringify({ originalUrl, ...(options.alias ? { alias: options.alias } : {}) }),
    signal,
  });
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401) {
      throw new LinkApiError('Your session is invalid. Please log in again.', undefined, 401);
    }
    if (typeof body === 'object' && body !== null && 'code' in body) {
      const field = 'field' in body && typeof body.field === 'string' ? body.field : undefined;
      const message = body.code === 'INVALID_URL'
        ? 'Enter a complete http:// or https:// URL, up to 8,192 characters.'
        : body.code === 'INVALID_REQUEST'
          ? 'The request could not be processed. Please try again.'
          : body.code === 'INVALID_ALIAS'
            ? 'Use 3–32 ASCII letters, numbers, hyphens or underscores. Aliases are case-sensitive.'
            : body.code === 'ALIAS_ALREADY_TAKEN'
              ? 'This alias is already taken. Choose another alias.'
          : 'Your link could not be created. Please try again later.';
      throw new LinkApiError(message, field, response.status);
    }
    throw new LinkApiError('Your link could not be created. Please try again later.');
  }
  if (!isCreatedLink(body)) throw new LinkApiError('The service returned an unexpected response. Please try again later.');
  return body;
}
