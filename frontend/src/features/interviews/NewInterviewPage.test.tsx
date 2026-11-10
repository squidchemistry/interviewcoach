import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setSession } from '../../api/session';
import { makeAuth, problem } from '../../test/fixtures';
import { makeSummary, SESSION_ID } from '../../test/interviewFixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { NewInterviewPage } from './NewInterviewPage';

describe('NewInterviewPage', () => {
  beforeEach(() => {
    setSession(makeAuth());
    server.use(
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())),
      http.get('*/api/v1/me/usage', () => HttpResponse.json({ period: '2026-11', plan: 'FREE', interviewsStarted: 2, interviewLimit: 3, tokensUsedToday: 0 })),
      http.get('*/api/v1/packs', () =>
        HttpResponse.json([{ slug: 'amazon-lp', name: 'Amazon LPs', description: '', mode: 'BEHAVIOURAL', company: 'amazon', pro: true, questionCount: 12 }]),
      ),
    );
  });

  it('shows usage, creates and starts the interview, then opens the room', async () => {
    let created: Record<string, unknown> | null = null;
    let started = false;
    server.use(
      http.post('*/api/v1/sessions', async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(makeSummary({ status: 'CREATED' }), { status: 201 });
      }),
      http.post(`*/api/v1/sessions/${SESSION_ID}/start`, () => {
        started = true;
        return HttpResponse.json({ status: 'IN_PROGRESS', stage: 'QUESTION', processing: true }, { status: 202 });
      }),
    );
    renderPage(<NewInterviewPage />, { path: '/interviews/new' });

    expect(await screen.findByText('2 of 3 free interviews used this month')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Behavioural (STAR)'));
    expect(screen.getByLabelText('Questions (3–6)')).toHaveValue('4');
    expect(await screen.findByRole('option', { name: /Amazon LPs .*Pro/ })).toBeDisabled();
    expect(screen.getByLabelText(/Voice mode/)).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText('Company focus'), 'amazon');
    await userEvent.click(screen.getByRole('button', { name: /Start interview/ }));

    expect(await screen.findByTestId('location')).toHaveTextContent(`/interviews/${SESSION_ID}`);
    expect(created).toEqual({ mode: 'BEHAVIOURAL', questionCount: 4, targetRole: 'SDE_1', company: 'amazon' });
    expect(started).toBe(true);
  });

  it('explains a 402 quota error and links to billing', async () => {
    server.use(
      http.post('*/api/v1/sessions', () => {
        const p = problem(402, 'QUOTA_EXCEEDED', 'Free plan limit reached');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<NewInterviewPage />, { path: '/interviews/new' });
    await userEvent.click(await screen.findByRole('button', { name: /Start interview/ }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/used all your free interviews/);
    expect(screen.getByRole('link', { name: 'See plans' })).toHaveAttribute('href', '/billing');
    await vi.waitFor(() => expect(screen.getByRole('button', { name: /Start interview/ })).toBeEnabled());
  });
});
