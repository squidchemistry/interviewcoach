import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '../../test/render';
import { healthyResponse, server } from '../../test/server';
import { HealthCard } from './HealthCard';

describe('HealthCard', () => {
  it('shows operational status and every dependency when the API is UP', async () => {
    renderWithProviders(<HealthCard />);

    expect(await screen.findByText('All systems operational')).toBeInTheDocument();
    const list = screen.getByRole('list', { name: 'Dependency status' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(4);
    expect(within(list).getByText('Vector search (pgvector)')).toBeInTheDocument();
    expect(screen.getByText('API version 0.1.0')).toBeInTheDocument();
  });

  it('shows degraded status with the failing dependency on 503', async () => {
    server.use(
      http.get('*/api/v1/health', () =>
        HttpResponse.json(
          { ...healthyResponse, status: 'DOWN', components: { ...healthyResponse.components, redis: 'DOWN' } },
          { status: 503 },
        ),
      ),
    );
    renderWithProviders(<HealthCard />);

    expect(await screen.findByText('Degraded: some dependencies are down')).toBeInTheDocument();
    const redisRow = screen.getByText('Redis').closest('li');
    expect(redisRow).toHaveTextContent('DOWN');
  });

  it('explains how to start the backend when the API is unreachable', async () => {
    server.use(http.get('*/api/v1/health', () => HttpResponse.error()));
    renderWithProviders(<HealthCard />);

    expect(await screen.findByText('API unreachable')).toBeInTheDocument();
    expect(screen.getByText(/docker compose up/)).toBeInTheDocument();
  });

  it('re-checks on demand via the Refresh button', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/health', () => {
        calls += 1;
        return HttpResponse.json(healthyResponse);
      }),
    );
    renderWithProviders(<HealthCard />);
    await screen.findByText('All systems operational');

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }));

    await screen.findByRole('button', { name: 'Refresh' });
    expect(calls).toBe(2);
  });
});
