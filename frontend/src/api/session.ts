import type { AuthResponse, UserResponse } from './types';
import { apiUrl } from './url';

/**
 * In-memory session. The access token is deliberately NOT persisted (localStorage is readable by any XSS);
 * after a reload the session is restored from the httpOnly refresh cookie via {@link refreshSession}.
 */
export interface SessionState {
  accessToken: string | null;
  user: UserResponse | null;
}

let state: SessionState = { accessToken: null, user: null };
const listeners = new Set<() => void>();

export function getSession(): SessionState {
  return state;
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function emit() {
  listeners.forEach((listener) => listener());
}

export function setSession(auth: AuthResponse) {
  state = { accessToken: auth.accessToken, user: auth.user };
  emit();
}

export function updateUser(user: UserResponse) {
  state = { ...state, user };
  emit();
}

export function clearSession() {
  if (state.accessToken !== null || state.user !== null) {
    state = { accessToken: null, user: null };
    emit();
  }
}

let inflight: Promise<AuthResponse | null> | null = null;

/**
 * Exchanges the refresh cookie for a new access token. Single-flight: concurrent callers (several requests
 * that all got 401) share one refresh, because each refresh rotates the cookie and a second parallel call
 * would present an already-rotated token.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  inflight ??= doRefresh().finally(() => {
    inflight = null;
  });
  return inflight;
}

const RACE_RETRY_DELAY_MS = 300;

async function doRefresh(): Promise<AuthResponse | null> {
  const hadSession = state.accessToken !== null;
  // A second attempt only makes sense when we had a session: another tab may have rotated the cookie at the
  // same moment, and the browser now holds the newer one.
  const attempts = hadSession ? 2 : 1;
  for (let attempt = 0; attempt < attempts; attempt++) {
    let response: Response | null = null;
    try {
      response = await fetch(apiUrl('/api/v1/auth/refresh'), {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      });
    } catch {
      return null; // offline: keep whatever session state we have
    }
    if (response.ok) {
      const auth = (await response.json()) as AuthResponse;
      setSession(auth);
      return auth;
    }
    if (response.status !== 401) {
      return null;
    }
    if (attempt + 1 < attempts) {
      await new Promise((resolve) => setTimeout(resolve, RACE_RETRY_DELAY_MS));
    }
  }
  clearSession();
  return null;
}
