export type ApiProblem = {
  status?: number;
  code?: string;
  detail?: string;
  fieldErrors?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(public readonly problem: ApiProblem) {
    super(problem.detail || 'The service is unavailable. Please try again later.');
  }
}

async function requestResponse(path: string, options: RequestInit): Promise<Response> {
  const response = await fetch(path, options);
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const problem: ApiProblem = { status: response.status };
    if (body && typeof body === 'object') {
      if ('code' in body && typeof body.code === 'string') problem.code = body.code;
      if ('detail' in body && typeof body.detail === 'string') problem.detail = body.detail;
      if ('fieldErrors' in body && body.fieldErrors && typeof body.fieldErrors === 'object') {
        problem.fieldErrors = Object.fromEntries(Object.entries(body.fieldErrors).filter((entry): entry is [string, string] => typeof entry[1] === 'string'));
      }
    }
    throw new ApiError(problem);
  }
  return response;
}

export async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await requestResponse(path, options);
  return response.json() as Promise<T>;
}

export async function requestEmpty(path: string, options: RequestInit = {}): Promise<void> {
  await requestResponse(path, options);
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return requestJson<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  });
}

export function getJson<T>(path: string, token: string, signal?: AbortSignal): Promise<T> {
  return requestJson<T>(path, { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` }, signal });
}
