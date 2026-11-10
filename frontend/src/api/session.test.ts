import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeAuth, problem } from '../test/fixtures';
import { server } from '../test/server';
import { ApiError, apiRequest } from './http';
import { getSession, setSession } from './session';

describe('authenticated requests', () => {
  it('sends the in-memory access token as a bearer token', async () => {
    setSession(makeAuth(undefined, 'token-abc'));
    server.use(
      http.get('*/api/v1/me', ({ request }) => HttpResponse.json({ auth: request.headers.get('Authorization') })),
    );
    await expect(apiRequest('/api/v1/me')).resolves.toEqual({ auth: 'Bearer token-abc' });
  });

  it('never sends the bearer token to /auth endpoints (they use the httpOnly cookie)', async () => {
    setSession(makeAuth(undefined, 'token-abc'));
    server.use(
      http.post('*/api/v1/auth/logout', ({ request }) =>
        request.headers.get('Authorization') ? HttpResponse.json({}, { status: 400 }) : new HttpResponse(null, { status: 204 }),
      ),
    );
    await expect(apiRequest('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    setSession(makeAuth(undefined, 'expired'));
    server.use(
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth(undefined, 'fresh'))),
      http.get('*/api/v1/me', ({ request }) => {
        if (request.headers.get('Authorization') === 'Bearer fresh') return HttpResponse.json({ ok: true });
        const p = problem(401, 'TOKEN_EXPIRED', 'The access token has expired');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    await expect(apiRequest('/api/v1/me')).resolves.toEqual({ ok: true });
    expect(getSession().accessToken).toBe('fresh');
  });

  it('shares a single refresh between concurrent 401s (the cookie rotates on every refresh)', async () => {
    setSession(makeAuth(undefined, 'expired'));
    let refreshCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        refreshCalls += 1;
        await new Promise((r) => setTimeout(r, 20));
        return HttpResponse.json(makeAuth(undefined, 'fresh'));
      }),
      http.get('*/api/v1/things/:id', ({ request }) => {
        if (request.headers.get('Authorization') === 'Bearer fresh') return HttpResponse.json({ ok: true });
        const p = problem(401, 'TOKEN_EXPIRED', 'expired');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    await Promise.all([apiRequest('/api/v1/things/1'), apiRequest('/api/v1/things/2'), apiRequest('/api/v1/things/3')]);
    expect(refreshCalls).toBe(1);
  });

  it('retries a refresh once when another tab won the rotation race', async () => {
    setSession(makeAuth(undefined, 'expired'));
    let refreshCalls = 0;
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refreshCalls += 1;
        if (refreshCalls === 1) {
          const p = problem(401, 'UNAUTHENTICATED', 'Refresh token has already been used');
          return HttpResponse.json(p.body, p.init);
        }
        return HttpResponse.json(makeAuth(undefined, 'fresh'));
      }),
      http.get('*/api/v1/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json({}, { status: 401 }),
      ),
    );
    await expect(apiRequest('/api/v1/me')).resolves.toEqual({ ok: true });
    expect(refreshCalls).toBe(2);
  });

  it('clears the session and surfaces 401 when the refresh fails', async () => {
    setSession(makeAuth(undefined, 'expired'));
    server.use(
      http.get('*/api/v1/me', () => {
        const p = problem(401, 'TOKEN_EXPIRED', 'expired');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    const error = await apiRequest('/api/v1/me').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect(getSession()).toEqual({ accessToken: null, user: null });
  });

  it('maps body field paths for forms and exposes Retry-After', async () => {
    server.use(
      http.post('*/api/v1/things', () =>
        HttpResponse.json(
          { status: 429, code: 'RATE_LIMITED', detail: 'Slow down' },
          { status: 429, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '42' } },
        ),
      ),
    );
    const limited = (await apiRequest('/api/v1/things', { method: 'POST', body: {} }).catch((e: unknown) => e)) as ApiError;
    expect(limited.retryAfterSeconds).toBe(42);

    const validation = new ApiError(400, {
      status: 400,
      code: 'VALIDATION_ERROR',
      errors: [
        { path: 'body.email', message: 'bad email' },
        { path: 'body.email', message: 'second message ignored' },
        { path: 'pageSize', message: 'too big' },
      ],
    });
    expect(validation.fieldErrors()).toEqual({ email: 'bad email', pageSize: 'too big' });
  });
});
