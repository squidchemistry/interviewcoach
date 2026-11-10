import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { HealthResponse } from '../api/types';
import { companies, problem } from './fixtures';

export const healthyResponse: HealthResponse = {
  status: 'UP',
  version: '0.1.0',
  components: { readinessState: 'UP', db: 'UP', redis: 'UP', pgvector: 'UP' },
};

/**
 * Default handlers: a healthy backend with an anonymous visitor (no refresh cookie) and Google disabled.
 * Individual tests override with `server.use(...)`.
 */
export const server = setupServer(
  http.get('*/api/v1/health', () => HttpResponse.json(healthyResponse)),
  http.post('*/api/v1/auth/refresh', () => {
    const p = problem(401, 'UNAUTHENTICATED', 'No refresh token');
    return HttpResponse.json(p.body, p.init);
  }),
  http.get('*/api/v1/auth/providers', () => HttpResponse.json({ password: true, google: false })),
  http.get('*/api/v1/companies', () => HttpResponse.json(companies)),
);
