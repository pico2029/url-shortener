export type Session = { accessToken: string; expiresAt: string; tokenType: string };
const sessionKey = 'shortener.session';

export function saveSession(session: Session) {
  sessionStorage.setItem(sessionKey, JSON.stringify(session));
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
