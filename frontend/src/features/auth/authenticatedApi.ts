import { ApiError, requestJson } from '../../shared/api';
import { navigate } from '../../shared/navigation';
import { clearSession, readSession, sessionIdentity } from './session';

const requests = new Set<AbortController>();

export function endSession(reason?: 'expired') {
  clearSession();
  for (const controller of requests) controller.abort();
  requests.clear();
  navigate(reason ? '/login?reason=expired' : '/login', true);
}

export function isCancelled(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

export async function authenticatedJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = readSession();
  if (!session) {
    endSession('expired');
    throw new DOMException('Session ended', 'AbortError');
  }
  const identity = sessionIdentity();
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener('abort', cancel, { once: true });
  requests.add(controller);
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  headers.set('Authorization', `Bearer ${session.accessToken}`);
  try {
    const result = await requestJson<T>(path, { ...options, headers: Object.fromEntries(headers.entries()), signal: controller.signal });
    if (controller.signal.aborted || sessionIdentity() !== identity) {
      throw new DOMException('Stale request', 'AbortError');
    }
    return result;
  } catch (error) {
    if (controller.signal.aborted || sessionIdentity() !== identity) {
      throw new DOMException('Stale request', 'AbortError');
    }
    if (error instanceof ApiError && error.problem.status === 401) {
      endSession('expired');
      throw new DOMException('Session ended', 'AbortError');
    }
    throw error;
  } finally {
    requests.delete(controller);
    options.signal?.removeEventListener('abort', cancel);
  }
}
