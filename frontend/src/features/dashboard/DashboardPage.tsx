import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { progressApi } from '../../api/endpoints';
import type { InterviewMode } from '../../api/models';
import { ModeBadge, StatusBadge } from '../../components/ui/badges';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { accents, buttonClass } from '../../components/ui/styles';
import { MODE_LABEL, formatDate, formatScore, humanize } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';
import { LineChart } from './LineChart';

function Tile({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className={`nb-accent ${color} p-4`}>
      <dt className="font-mono text-xs font-bold uppercase">{label}</dt>
      <dd className="nb-heading mt-1 text-4xl">{value}</dd>
    </div>
  );
}

export function DashboardPage() {
  const [mode, setMode] = useState<InterviewMode | ''>('');
  const progress = useQuery({ queryKey: ['progress', mode], queryFn: () => progressApi.progress(mode) });
  const [competency, setCompetency] = useState('');

  if (progress.isPending) return <PageSpinner label="Loading your progress" />;
  if (progress.isError) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <Alert>{describeError(progress.error)}</Alert>
      </div>
    );
  }

  const data = progress.data;
  const competencies = [...new Set(data.trends.map((t) => t.competency))];
  const selected = competency && competencies.includes(competency) ? competency : competencies[0];
  const trend = data.trends
    .filter((t) => t.competency === selected)
    .sort((a, b) => a.at.localeCompare(b.at))
    .map((t) => ({ at: t.at, value: t.score, label: MODE_LABEL[t.mode] }));
  const scores = [...data.scores].sort((a, b) => a.at.localeCompare(b.at)).map((s) => ({ at: s.at, value: s.overallScore, label: MODE_LABEL[s.mode] }));

  const filters = (
    <div>
      <label htmlFor="dashboard-mode" className="nb-label">
        Round
      </label>
      <select id="dashboard-mode" className="nb-input w-auto" value={mode} onChange={(e) => setMode(e.target.value as InterviewMode | '')}>
        <option value="">All rounds</option>
        {(Object.keys(MODE_LABEL) as InterviewMode[]).map((m) => (
          <option key={m} value={m}>
            {MODE_LABEL[m]}
          </option>
        ))}
      </select>
    </div>
  );

  if (data.totals.interviews === 0 && mode === '') {
    return (
      <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
        <PageHeader title="Your dashboard" kicker="Progress" />
        <EmptyState
          title="No interviews yet"
          color="bg-nb-yellow"
          action={
            <Link to="/interviews/new" className={buttonClass.ink}>
              Start your first interview →
            </Link>
          }
        >
          Finish a mock interview and your scores, competency trends and weak topics will show up here.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader
        title="Your dashboard"
        kicker="Progress"
        actions={
          <Link to="/interviews/new" className={buttonClass.primary}>
            New interview
          </Link>
        }
      />
      {filters}

      <dl className="grid grid-cols-2 gap-4 md:grid-cols-5">
        <Tile label="Interviews" value={String(data.totals.interviews)} color="bg-nb-yellow" />
        <Tile label="Completed" value={String(data.totals.completed)} color="bg-nb-green" />
        <Tile label="Average" value={formatScore(data.totals.averageScore)} color="bg-nb-blue" />
        <Tile label="Best" value={formatScore(data.totals.bestScore)} color="bg-nb-pink" />
        <Tile label="Questions" value={String(data.totals.questionsAnswered)} color="bg-nb-violet" />
      </dl>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="scores-heading" className="nb-card p-5">
          <h2 id="scores-heading" className="nb-heading text-xl">
            Overall score over time
          </h2>
          {scores.length === 0 ? (
            <p className="mt-3 font-medium text-muted">No scored interviews yet.</p>
          ) : (
            <LineChart
              title="Overall score over time"
              description={`${scores.length} scored interviews, from ${formatScore(scores[0]!.value)} to ${formatScore(scores[scores.length - 1]!.value)} out of 100.`}
              points={scores}
              max={100}
              unit=""
            />
          )}
        </section>
        <section aria-labelledby="trend-heading" className="nb-card p-5">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h2 id="trend-heading" className="nb-heading text-xl">
              Competency trend
            </h2>
            {competencies.length > 0 && (
              <div>
                <label htmlFor="competency" className="sr-only">
                  Competency
                </label>
                <select id="competency" className="nb-input w-auto py-1 text-sm" value={selected} onChange={(e) => setCompetency(e.target.value)}>
                  {competencies.map((c) => (
                    <option key={c} value={c}>
                      {humanize(c)}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
          {trend.length === 0 ? (
            <p className="mt-3 font-medium text-muted">No competency scores yet.</p>
          ) : (
            <LineChart
              title={`${humanize(selected ?? '')} score over time`}
              description={`${trend.length} sessions scored on ${humanize(selected ?? '')}, on a 0 to 10 scale.`}
              points={trend}
              max={10}
              unit="/10"
            />
          )}
        </section>
      </div>

      {data.modes.length > 0 && (
        <section aria-labelledby="modes-heading">
          <h2 id="modes-heading" className="nb-heading text-2xl">
            By round
          </h2>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {data.modes.map((m, i) => (
              <li key={m.mode} className="nb-card p-4">
                <ModeBadge mode={m.mode} />
                <p className="nb-heading mt-3 text-3xl">{formatScore(m.averageScore)}</p>
                <p className="text-sm font-medium text-muted">
                  average over {m.completed} completed
                </p>
                <Link to={`/interviews/new?mode=${m.mode}`} className={`mt-3 inline-block nb-btn nb-btn-sm ${i % 2 ? 'nb-btn-secondary' : 'nb-btn-primary'}`}>
                  Practise {MODE_LABEL[m.mode] ?? m.mode}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="weak-heading" className="nb-card p-5">
          <h2 id="weak-heading" className="nb-heading text-xl">
            Weak topics
          </h2>
          {data.weakTopics.length === 0 ? (
            <p className="mt-3 font-medium text-muted">Nothing flagged yet — topics appear after a couple of attempts.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {data.weakTopics.map((t, i) => (
                <li key={t.tag} className="flex flex-wrap items-center justify-between gap-2 border-2 border-ink p-2">
                  <span>
                    <span className={`nb-badge ${accents[i % accents.length]}`}>#{t.tag}</span>
                    <span className="ml-2 text-sm font-medium">
                      avg {t.averageScore.toFixed(1)}/10 · {t.attempts} attempts
                    </span>
                  </span>
                  <Link to={`/questions?tag=${encodeURIComponent(t.tag)}`} className={`${buttonClass.secondary} nb-btn-sm`}>
                    Practice<span className="sr-only"> {t.tag}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section aria-labelledby="recent-heading" className="nb-card p-5">
          <h2 id="recent-heading" className="nb-heading text-xl">
            Recent interviews
          </h2>
          <ul className="mt-3 space-y-2">
            {data.recent.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 border-2 border-ink p-2">
                <span className="flex flex-wrap items-center gap-2">
                  <ModeBadge mode={s.mode} />
                  <StatusBadge status={s.status} />
                  <span className="font-mono text-xs">{formatDate(s.createdAt)}</span>
                </span>
                <span className="flex items-center gap-2">
                  <span className="font-mono font-bold">{formatScore(s.overallScore)}</span>
                  <Link
                    to={s.status === 'COMPLETED' || s.status === 'EVALUATING' ? `/interviews/${s.id}/report` : `/interviews/${s.id}`}
                    className="nb-link text-sm"
                  >
                    Open<span className="sr-only"> {MODE_LABEL[s.mode]} interview of {formatDate(s.createdAt)}</span>
                  </Link>
                </span>
              </li>
            ))}
          </ul>
          <Link to="/interviews" className="nb-link mt-3 inline-block text-sm">
            All interviews →
          </Link>
        </section>
      </div>
    </div>
  );
}

export default DashboardPage;
