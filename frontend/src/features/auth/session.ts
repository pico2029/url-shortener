export type Session = { accessToken: string; expiresAt: string; tokenType: string };
const sessionKey = 'shortener.session';

export function saveSession(session: Session) {
  sessionStorage.setItem(sessionKey, JSON.stringify({ ...session, sessionId: crypto.randomUUID() }));
}

export function clearSession() {
  sessionStorage.removeItem(sessionKey);
}

export function readSession(): Session | null {
  try {
    const session = JSON.parse(sessionStorage.getItem(sessionKey) || 'null') as Session | null;
    if (session && typeof session.accessToken === 'string' && session.accessToken
        && Number.isFinite(Date.parse(session.expiresAt)) && Date.parse(session.expiresAt) > Date.now()) {
      return session;
    }
  } catch { /* Unreadable storage is treated as an expired session. */ }
  clearSession();
  return null;
}

// Read the token without discarding expired sessions, for request identity checks.
export function sessionToken(): string | null {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(sessionKey) || 'null');
    return value && typeof value === 'object' && 'accessToken' in value && typeof value.accessToken === 'string'
      ? value.accessToken : null;
  } catch { return null; }
}

// The identity changes on every login, even if the server issues the same JWT.
export function sessionIdentity(): string | null {
  return sessionStorage.getItem(sessionKey);
}
