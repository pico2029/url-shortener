export interface CreatedLink {
  originalUrl: string;
  shortCode: string;
  shortUrl: string;
}

export class LinkApiError extends Error {
  constructor(message: string, readonly field?: string) {
    super(message);
  }
}

function isCreatedLink(value: unknown): value is CreatedLink {
  return typeof value === 'object' && value !== null
    && 'originalUrl' in value && typeof value.originalUrl === 'string'
    && 'shortCode' in value && typeof value.shortCode === 'string'
    && 'shortUrl' in value && typeof value.shortUrl === 'string';
}

export async function createLink(originalUrl: string, signal: AbortSignal): Promise<CreatedLink> {
  const response = await fetch('/api/v1/links', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ originalUrl }),
    signal,
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    if (typeof body === 'object' && body !== null && 'code' in body) {
      const field = 'field' in body && typeof body.field === 'string' ? body.field : undefined;
      const message = body.code === 'INVALID_URL'
        ? 'Enter a complete http:// or https:// URL, up to 8,192 characters.'
        : body.code === 'INVALID_REQUEST'
          ? 'The request could not be processed. Please try again.'
          : 'Your link could not be created. Please try again later.';
      throw new LinkApiError(message, field);
    }
    throw new LinkApiError('Your link could not be created. Please try again later.');
  }
  if (!isCreatedLink(body)) throw new LinkApiError('The service returned an unexpected response. Please try again later.');
  return body;
}
