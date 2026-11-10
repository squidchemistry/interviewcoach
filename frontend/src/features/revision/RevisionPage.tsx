import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { progressApi } from '../../api/endpoints';
import type { RevisionItem } from '../../api/models';
import { DifficultyBadge } from '../../components/ui/badges';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { buttonClass } from '../../components/ui/styles';
import { QUESTION_TYPE_LABEL, formatDate } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

function RevisionCard({ item }: { item: RevisionItem }) {
  const practiceMode = item.suggestedQuestions[0]?.type ?? 'DSA';
  return (
    <li className={`nb-card flex flex-col gap-3 p-5 ${item.due ? 'border-l-[10px]' : ''}`}>
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="nb-heading text-xl">#{item.tag}</h3>
        {item.due ? <span className="nb-badge bg-nb-red">Due now</span> : <span className="nb-badge bg-nb-blue">Due {formatDate(item.dueAt)}</span>}
      </div>
      <p className="text-sm font-medium">
        Average {item.averageScore.toFixed(1)}/10 over {item.attempts} {item.attempts === 1 ? 'attempt' : 'attempts'} · next interval {item.intervalDays}{' '}
        {item.intervalDays === 1 ? 'day' : 'days'}
      </p>
      {item.suggestedQuestions.length > 0 && (
        <ul className="space-y-1.5" aria-label={`Suggested questions for ${item.tag}`}>
          {item.suggestedQuestions.map((q) => (
            <li key={q.id} className="flex flex-wrap items-center gap-2">
              <DifficultyBadge difficulty={q.difficulty} />
              <span className="nb-badge bg-surface">{QUESTION_TYPE_LABEL[q.type] ?? q.type}</span>
              <Link to={`/questions/${encodeURIComponent(q.slug)}`} className="nb-link text-sm">
                {q.title}
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Link to={`/interviews/new?mode=${practiceMode}`} className={`${item.due ? buttonClass.primary : buttonClass.secondary} nb-btn-sm mt-auto w-fit`}>
        Practise now<span className="sr-only"> ({item.tag})</span>
      </Link>
    </li>
  );
}

export function RevisionPage() {
  const revision = useQuery({ queryKey: ['revision'], queryFn: progressApi.revision });

  if (revision.isPending) return <PageSpinner label="Loading your revision queue" />;

  const items = [...(revision.data?.items ?? [])].sort((a, b) => Number(b.due) - Number(a.due) || a.dueAt.localeCompare(b.dueAt));
  const due = items.filter((i) => i.due);
  const later = items.filter((i) => !i.due);

  return (
    <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader
        title="Revision queue"
        kicker="Spaced repetition"
        kickerColor="bg-nb-orange"
        description="Topics come back on a schedule based on how well you did — weaker topics return sooner."
        actions={
          <Link to="/interviews/new" className={buttonClass.primary}>
            Start a practice interview
          </Link>
        }
      />
      {revision.isError && <Alert>{describeError(revision.error)}</Alert>}
      {revision.isSuccess && items.length === 0 && (
        <EmptyState
          title="Nothing to revise yet"
          action={
            <Link to="/interviews/new" className={buttonClass.ink}>
              Take an interview →
            </Link>
          }
        >
          After your interviews are evaluated, each topic you were tested on is scheduled for revision here.
        </EmptyState>
      )}
      {due.length > 0 && (
        <section aria-labelledby="due-heading">
          <h2 id="due-heading" className="nb-heading text-2xl">
            Due now ({due.length})
          </h2>
          <ul className="mt-4 grid gap-5 md:grid-cols-2">
            {due.map((item) => (
              <RevisionCard key={item.tag} item={item} />
            ))}
          </ul>
        </section>
      )}
      {later.length > 0 && (
        <section aria-labelledby="later-heading">
          <h2 id="later-heading" className="nb-heading text-2xl">
            Coming up
          </h2>
          <ul className="mt-4 grid gap-5 md:grid-cols-2">
            {later.map((item) => (
              <RevisionCard key={item.tag} item={item} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
