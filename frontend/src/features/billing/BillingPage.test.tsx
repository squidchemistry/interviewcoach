import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { setSession } from '../../api/session';
import { makeAuth, makeUser } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { Layout } from '../../app/Layout';
import { Route, Routes } from 'react-router-dom';
import { BillingPage } from './BillingPage';

const plans = [
  { id: 'FREE', name: 'Free', priceInPaise: 0, currency: 'INR', interval: 'MONTH', features: ['3 interviews a month'] },
  { id: 'PRO_MONTHLY', name: 'Pro', priceInPaise: 49900, currency: 'INR', interval: 'MONTH', features: ['Unlimited interviews', 'Voice mode'] },
];

describe('BillingPage', () => {
  beforeEach(() => {
    setSession(makeAuth());
    server.use(
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())),
      http.get('*/api/v1/billing/plans', () => HttpResponse.json(plans)),
    );
  });

  it('runs the clearly-labelled mock checkout and refreshes the plan badge', async () => {
    let completedFor: string | null = null;
    server.use(
      http.get('*/api/v1/billing/subscription', () => new HttpResponse(null, { status: 204 })),
      http.post('*/api/v1/billing/subscriptions', () =>
        HttpResponse.json({ provider: 'MOCK', subscriptionId: 'sub_mock_1', priceInPaise: 49900 }, { status: 201 }),
      ),
      http.post('*/api/v1/billing/mock/complete', async ({ request }) => {
        completedFor = ((await request.json()) as { subscriptionId: string }).subscriptionId;
        return HttpResponse.json({ id: 'b-1', provider: 'MOCK', planId: 'PRO_MONTHLY', status: 'ACTIVE', currentEnd: '2026-12-06T00:00:00Z', createdAt: '2026-11-06T00:00:00Z' });
      }),
      http.get('*/api/v1/me', () => HttpResponse.json(makeUser({ plan: 'PRO' }))),
    );
    renderWithProviders(
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<BillingPage />} />
        </Route>
      </Routes>,
    );

    expect(await screen.findByText('₹499')).toBeInTheDocument();
    expect(screen.getByText(/No subscription/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Upgrade to Pro' }));

    const panel = await screen.findByRole('region', { name: 'Simulated checkout' });
    expect(within(panel).getByText('Test mode — no real payment')).toBeInTheDocument();
    await userEvent.click(within(panel).getByRole('button', { name: 'Complete test payment' }));

    expect(await screen.findByText(/Welcome to Pro!/)).toBeInTheDocument();
    expect(completedFor).toBe('sub_mock_1');
    expect((await screen.findAllByRole('link', { name: 'Plan: Pro' })).length).toBeGreaterThan(0);
    expect(screen.getByText('Your current plan')).toBeInTheDocument();
  });
});
