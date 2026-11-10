import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { PublicReport } from '../../api/models';
import { problem } from '../../test/fixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { PublicReportPage } from './PublicReportPage';

const report: PublicReport = {
  candidateFirstName: 'Priya',
  mode: 'DSA',
  targetRole: 'SDE_1',
  company: 'amazon',
  completedAt: '2026-11-05T10:30:00Z',
  overallScore: 81,
  confidence: 'MEDIUM',
  summary: 'Solid problem solving.',
  competencies: [{ competency: 'PROBLEM_SOLVING', label: 'Problem solving', score: 8.2, lowConfidence: false }],
  strengths: ['Clear complexity analysis', 'Good tests'],
  gaps: ['Edge cases', 'Naming'],
  questions: [{ position: 0, title: 'Two Sum', difficulty: 'EASY', turnScore: 8 }],
};

describe('PublicReportPage', () => {
  it('renders the anonymised report without auth', async () => {
    let authorization: string | null = 'unset';
    server.use(
      http.get('*/api/v1/public/reports/tok123', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json(report);
      }),
    );
    renderPage(<PublicReportPage />, { path: '/r/:token', route: '/r/tok123' });

    expect(await screen.findByRole('heading', { level: 1, name: "Priya's DSA coding interview" })).toBeInTheDocument();
    expect(screen.getByText('81')).toBeInTheDocument();
    expect(screen.getByRole('meter', { name: 'Problem solving' })).toBeInTheDocument();
    expect(screen.getByText('Clear complexity analysis')).toBeInTheDocument();
    expect(screen.getByText('Two Sum')).toBeInTheDocument();
    expect(screen.queryByText(/transcript/i)).not.toBeInTheDocument();
    expect(authorization).toBeNull();
  });

  it('shows a neutral message for expired or revoked links', async () => {
    server.use(
      http.get('*/api/v1/public/reports/gone', () => {
        const p = problem(404, 'NOT_FOUND', 'Not found');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<PublicReportPage />, { path: '/r/:token', route: '/r/gone' });
    expect(await screen.findByRole('heading', { name: 'Link unavailable' })).toBeInTheDocument();
  });
});
