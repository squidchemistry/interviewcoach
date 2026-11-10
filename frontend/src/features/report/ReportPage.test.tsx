import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setSession } from '../../api/session';
import { makeAuth } from '../../test/fixtures';
import { makeReport, SESSION_ID } from '../../test/interviewFixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { ReportPage } from './ReportPage';

const route = { path: '/interviews/:id/report', route: `/interviews/${SESSION_ID}/report` };

describe('ReportPage', () => {
  beforeEach(() => {
    setSession(makeAuth());
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())));
  });
  afterEach(() => vi.useRealTimers());

  it('renders the overall score, competencies, evidence, next steps, per-question details and integrity notes', async () => {
    server.use(http.get(`*/api/v1/sessions/${SESSION_ID}/report`, () => HttpResponse.json(makeReport())));
    renderPage(<ReportPage />, route);

    const hero = await screen.findByRole('heading', { name: 'Overall score' });
    expect(hero.parentElement).toHaveTextContent('72');
    expect(screen.getByText('high confidence')).toBeInTheDocument();
    expect(screen.getByRole('meter', { name: 'Communication' })).toHaveAttribute('aria-valuenow', '7.5');
    expect(screen.getByRole('meter', { name: 'Problem solving (low confidence)' })).toBeInTheDocument();
    expect(screen.getAllByText(/We cut failed deploys by 80%/).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Turn 2' })).toHaveAttribute('href', '#turn-2');

    const steps = screen.getByRole('heading', { name: 'Your next steps' }).nextElementSibling as HTMLElement;
    expect(within(steps).getAllByRole('listitem')).toHaveLength(3);
    expect(within(steps).getByRole('link', { name: 'Practise #conflict' })).toHaveAttribute('href', '/questions?tag=conflict');

    expect(screen.getByRole('table', { name: 'STAR breakdown' })).toBeInTheDocument();
    expect(screen.getByText('Large paste (1200 chars)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create share link' })).toBeInTheDocument();
  });

  it('polls every 3 s while the evaluation is pending, then shows the scores', async () => {
    let calls = 0;
    server.use(
      http.get(`*/api/v1/sessions/${SESSION_ID}/report`, () => {
        calls += 1;
        if (calls === 1) {
          const pending = makeReport({ status: 'RUNNING', overallScore: undefined, competencies: undefined });
          pending.session.status = 'EVALUATING';
          return HttpResponse.json(pending);
        }
        return HttpResponse.json(makeReport());
      }),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage(<ReportPage />, route);

    expect(await screen.findByRole('heading', { name: 'Evaluating your interview…' })).toBeInTheDocument();
    expect(screen.getByText(/Status: Scoring/)).toBeInTheDocument();
    await vi.advanceTimersByTimeAsync(3100);
    expect(await screen.findByRole('heading', { name: 'Overall score' })).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  it('offers to retry a failed evaluation', async () => {
    let retried = false;
    server.use(
      http.get(`*/api/v1/sessions/${SESSION_ID}/report`, () => HttpResponse.json(makeReport({ status: 'FAILED', attempts: 2, error: 'Evaluator timed out' }))),
      http.post(`*/api/v1/sessions/${SESSION_ID}/evaluation/retry`, () => {
        retried = true;
        return new HttpResponse(null, { status: 202 });
      }),
    );
    renderPage(<ReportPage />, route);
    expect(await screen.findByText(/Evaluator timed out/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry evaluation' }));
    await vi.waitFor(() => expect(retried).toBe(true));
  });

  it('creates a share link and shows it once with a copy button', async () => {
    server.use(
      http.get(`*/api/v1/sessions/${SESSION_ID}/report`, () => HttpResponse.json(makeReport())),
      http.post(`*/api/v1/sessions/${SESSION_ID}/share`, async ({ request }) => {
        const { expiresInDays } = (await request.json()) as { expiresInDays: number };
        return HttpResponse.json(
          { id: 's-1', url: '/r/tok123', expiresAt: '2026-11-20T00:00:00Z', viewCount: 0, createdAt: '2026-11-06T00:00:00Z', days: expiresInDays },
          { status: 201 },
        );
      }),
    );
    renderPage(<ReportPage />, route);
    await userEvent.click(await screen.findByRole('button', { name: 'Create share link' }));
    expect(await screen.findByLabelText('Share link')).toHaveValue(`${window.location.origin}/r/tok123`);
    expect(screen.getByRole('button', { name: 'Copy' })).toBeInTheDocument();
  });
});
