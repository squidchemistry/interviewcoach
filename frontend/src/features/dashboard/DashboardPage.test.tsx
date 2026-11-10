import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { ProgressResponse } from '../../api/models';
import { setSession } from '../../api/session';
import { makeAuth } from '../../test/fixtures';
import { makeSummary } from '../../test/interviewFixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { DashboardPage } from './DashboardPage';

const empty: ProgressResponse = {
  totals: { interviews: 0, completed: 0, questionsAnswered: 0 },
  modes: [],
  scores: [],
  trends: [],
  weakTopics: [],
  recent: [],
};

describe('DashboardPage', () => {
  beforeEach(() => {
    setSession(makeAuth());
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())));
  });

  it('shows an empty state with a call to action when there are no interviews', async () => {
    server.use(http.get('*/api/v1/me/progress', () => HttpResponse.json(empty)));
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByRole('heading', { name: 'No interviews yet' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Start your first interview →' })).toHaveAttribute('href', '/interviews/new');
  });

  it('renders totals, an accessible score chart with a data table, and weak topics with practice links', async () => {
    server.use(
      http.get('*/api/v1/me/progress', () =>
        HttpResponse.json({
          totals: { interviews: 3, completed: 2, averageScore: 64.5, bestScore: 78, questionsAnswered: 7 },
          modes: [{ mode: 'BEHAVIOURAL', completed: 2, averageScore: 64.5 }],
          scores: [
            { sessionId: 'a', mode: 'BEHAVIOURAL', overallScore: 51, at: '2026-11-01T10:00:00Z' },
            { sessionId: 'b', mode: 'BEHAVIOURAL', overallScore: 78, at: '2026-11-04T10:00:00Z' },
          ],
          trends: [{ sessionId: 'a', mode: 'BEHAVIOURAL', competency: 'COMMUNICATION', score: 6.5, at: '2026-11-01T10:00:00Z' }],
          weakTopics: [{ tag: 'conflict', attempts: 2, averageScore: 4.5, lastScore: 4, dueAt: '2026-11-08T00:00:00Z', intervalDays: 1 }],
          recent: [makeSummary({ status: 'COMPLETED', overallScore: 78 })],
        } satisfies ProgressResponse),
      ),
    );
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByRole('img', { name: /Overall score over time/ })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Overall score over time' })).toBeInTheDocument();
    expect(screen.getAllByText('65').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Practice conflict' })).toHaveAttribute('href', '/questions?tag=conflict');
  });
});
