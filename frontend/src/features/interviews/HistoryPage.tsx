import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { sessionsApi } from '../../api/endpoints';
import type { InterviewMode, SessionStatus } from '../../api/models';
import { ModeBadge, StatusBadge } from '../../components/ui/badges';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { buttonClass } from '../../components/ui/styles';
import { MODE_LABEL, STATUS_LABEL, formatDateTime, formatScore } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

const PAGE_SIZE = 15;

export function HistoryPage() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as SessionStatus | '';
  const mode = (params.get('mode') ?? '') as InterviewMode | '';
  const page = Math.max(1, Number(params.get('page')) || 1);

  const sessions = useQuery({
    queryKey: ['sessions', status, mode, page],
    queryFn: () => sessionsApi.list({ status, mode, page, pageSize: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    setParams(next, { replace: true });
  }

  const filtered = status !== '' || mode !== '';

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <PageHeader
        title="Interview history"
        kicker="History"
        kickerColor="bg-nb-blue"
        actions={
          <Link to="/interviews/new" className={buttonClass.primary}>
            New interview
          </Link>
        }
      />

      <div className="flex flex-col gap-4 sm:flex-row">
        <div>
          <label htmlFor="status-filter" className="nb-label">
            Status
          </label>
          <select id="status-filter" className="nb-input" value={status} onChange={(e) => update({ status: e.target.value, page: '' })}>
            <option value="">All statuses</option>
            {(Object.keys(STATUS_LABEL) as SessionStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="mode-filter" className="nb-label">
            Round
          </label>
          <select id="mode-filter" className="nb-input" value={mode} onChange={(e) => update({ mode: e.target.value, page: '' })}>
            <option value="">All rounds</option>
            {(Object.keys(MODE_LABEL) as InterviewMode[]).map((m) => (
              <option key={m} value={m}>
                {MODE_LABEL[m]}
              </option>
            ))}
          </select>
        </div>
      </div>

      {sessions.isError && <Alert>{describeError(sessions.error)}</Alert>}

      {sessions.data && sessions.data.total === 0 && !filtered ? (
        <EmptyState
          title="No interviews yet"
          action={
            <Link to="/interviews/new" className={buttonClass.ink}>
              Start your first interview →
            </Link>
          }
        >
          Your completed and in-progress interviews will be listed here.
        </EmptyState>
      ) : (
        <>
          <div className="nb-card overflow-x-auto">
            <table className="nb-table min-w-full text-sm">
              <caption className="sr-only">Your interviews, newest first</caption>
              <thead>
                <tr>
                  <th scope="col">Started</th>
                  <th scope="col">Round</th>
                  <th scope="col">Status</th>
                  <th scope="col">Questions</th>
                  <th scope="col">Score</th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {sessions.data?.items.map((s) => (
                  <tr key={s.id}>
                    <td className="whitespace-nowrap font-mono text-xs">{formatDateTime(s.startedAt ?? s.createdAt)}</td>
                    <td>
                      <ModeBadge mode={s.mode} />
                      {s.company && <span className="ml-2 text-xs font-bold">@ {s.company}</span>}
                    </td>
                    <td>
                      <StatusBadge status={s.status} />
                    </td>
                    <td className="font-mono">
                      {Math.min(s.currentIndex + (s.status === 'COMPLETED' ? 0 : 1), s.plannedQuestions)}/{s.plannedQuestions}
                    </td>
                    <td className="font-mono text-lg font-bold">{formatScore(s.overallScore)}</td>
                    <td className="text-right">
                      {s.status === 'COMPLETED' || s.status === 'EVALUATING' ? (
                        <Link to={`/interviews/${s.id}/report`} className={`${buttonClass.secondary} nb-btn-sm`}>
                          Report<span className="sr-only"> for {MODE_LABEL[s.mode]} interview of {formatDateTime(s.createdAt)}</span>
                        </Link>
                      ) : s.status === 'ABANDONED' ? (
                        <span className="font-mono text-xs">—</span>
                      ) : (
                        <Link to={`/interviews/${s.id}`} className={`${buttonClass.primary} nb-btn-sm`}>
                          Resume<span className="sr-only"> {MODE_LABEL[s.mode]} interview of {formatDateTime(s.createdAt)}</span>
                        </Link>
                      )}
                    </td>
                  </tr>
                ))}
                {sessions.data && sessions.data.items.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-6 text-center font-medium">
                      No interviews match these filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <Pagination page={page} pageSize={PAGE_SIZE} total={sessions.data?.total} noun="interviews" onPage={(p) => update({ page: String(p) })} />
        </>
      )}
    </div>
  );
}
