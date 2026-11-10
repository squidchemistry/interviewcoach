import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { clearSession } from '../api/session';
import { server } from './server';

let fetchBeforeBridge: typeof fetch;

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
  // Test-environment bridge: under jsdom, AbortSignal is jsdom's class, but fetch is Node's (undici),
  // which rejects foreign signals ("Expected signal to be an instance of AbortSignal"). React Query
  // passes a signal to every query, so strip it here. Installed after MSW so it wraps MSW's interceptor.
  // Browsers are unaffected: there, both come from the same realm.
  fetchBeforeBridge = globalThis.fetch;
  globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) =>
    fetchBeforeBridge(input, init ? { ...init, signal: undefined } : init)) as typeof fetch;
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  clearSession();
});

afterAll(() => {
  globalThis.fetch = fetchBeforeBridge;
  server.close();
});
