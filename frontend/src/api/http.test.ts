import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { ApiError, apiRequest } from './http';

describe('apiRequest', () => {
  it('returns parsed JSON for 2xx responses', async () => {
    server.use(http.get('*/api/v1/things', () => HttpResponse.json({ id: 1 })));
    await expect(apiRequest<{ id: number }>('/api/v1/things')).resolves.toEqual({ id: 1 });
  });

  it('sends JSON bodies with a content type', async () => {
    server.use(
      http.post('*/api/v1/things', async ({ request }) =>
        HttpResponse.json({ contentType: request.headers.get('Content-Type'), body: await request.json() }),
      ),
    );
    const result = await apiRequest<{ contentType: string; body: unknown }>('/api/v1/things', {
      method: 'POST',
      body: { name: 'x' },
    });
    expect(result).toEqual({ contentType: 'application/json', body: { name: 'x' } });
  });

  it('turns problem+json errors into ApiError with code and requestId', async () => {
    server.use(
      http.get('*/api/v1/things', () =>
        HttpResponse.json(
          { status: 404, title: 'Resource not found', detail: 'Thing 42 was not found', code: 'NOT_FOUND', requestId: 'req-1' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );
    const error = await apiRequest('/api/v1/things').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404, code: 'NOT_FOUND', requestId: 'req-1', message: 'Thing 42 was not found' });
  });

  it('falls back to the X-Request-Id header for non-JSON errors', async () => {
    server.use(
      http.get('*/api/v1/things', () => new HttpResponse('Bad gateway', { status: 502, headers: { 'X-Request-Id': 'gw-1' } })),
    );
    const error = await apiRequest('/api/v1/things').catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 502, code: 'UNKNOWN', requestId: 'gw-1' });
  });

  it('accepts explicitly allowed non-2xx statuses', async () => {
    server.use(http.get('*/api/v1/things', () => HttpResponse.json({ degraded: true }, { status: 503 })));
    await expect(apiRequest('/api/v1/things', { acceptStatuses: [503] })).resolves.toEqual({ degraded: true });
  });

  it('reports network failures as NETWORK_ERROR', async () => {
    server.use(http.get('*/api/v1/things', () => HttpResponse.error()));
    const error = await apiRequest('/api/v1/things').catch((e: unknown) => e);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
