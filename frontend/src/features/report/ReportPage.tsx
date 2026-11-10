import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { sessionsApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { Competency, Evaluation, QuestionReport, Report, ShareLink, StarBreakdown, Turn } from '../../api/models';
import { DifficultyBadge, ModeBadge, TagList } from '../../components/ui/badges';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Alert } from '../../components/ui/form';
import { Markdown } from '../../components/ui/Markdown';
import { PageHeader } from '../../components/ui/PageHeader';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { StatusPage } from '../../components/ui/StatusPage';
import { accents, buttonClass } from '../../components/ui/styles';
import { EVALUATION_LABEL, LANGUAGE_LABEL, ROLE_LABEL, formatDate, formatDateTime, humanize } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';
import { CompetencyBar, ScoreHero, StrengthsGaps } from './ReportParts';

const POLL_MS = 3000;

function finished(report: Report | undefined): boolean {
  const status = report?.evaluation?.status;
  return status === 'SUCCEEDED' || status === 'FAILED';
}

function CompetencySection({ competencies, transcript }: { competencies: Competency[]; transcript: Turn[] }) {
  return (
    <section aria-labelledby="competency-heading" className="nb-card p-6">
      <h2 id="competency-heading" className="nb-heading text-2xl">
        Competencies
      </h2>
      <p className="mt-1 text-sm font-medium text-muted">
        Scored 0–10 against the rubric. Striped bars have low confidence: there wasn't enough evidence to score them fully.
      </p>
      <ul className="mt-5 space-y-6">
        {competencies.map((c) => (
          <li key={c.competency}>
            <CompetencyBar label={c.label} score={c.score} lowConfidence={c.lowConfidence} weight={c.weight} />
            {c.rationale && <p className="mt-2 font-medium">{c.rationale}</p>}
            {c.evidence && c.evidence.length > 0 && (
              <div className="mt-2 space-y-2">
                {c.evidence.map((e, i) => {
                  const turn = transcript.find((t) => t.seq === e.turnSeq);
                  return (
                    <blockquote key={i} className="border-l-4 border-ink bg-paper px-3 py-2 text-sm">
                      <p className="italic">“{e.quote}”</p>
                      <footer className="mt-1 font-mono text-xs font-bold">
                        <a href={`#turn-${e.turnSeq}`} className="nb-link">
                          Turn {e.turnSeq}
                        </a>
                        {turn ? ` · ${turn.role === 'CANDIDATE' ? 'your answer' : 'interviewer'}` : ''}
                      </footer>
                    </blockquote>
                  );
                })}
              </div>
            )}
            {c.adjustments && c.adjustments.length > 0 && (
              <ul className="mt-2 space-y-1 text-xs font-medium">
                {c.adjustments.map((a) => (
                  <li key={a} className="border-2 border-dashed border-ink px-2 py-1">
                    <span className="font-mono font-bold">Adjustment: </span>
                    {a}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function StarTable({ star }: { star: StarBreakdown }) {
  const parts = [
    ['Situation', star.situation],
    ['Task', star.task],
    ['Action', star.action],
    ['Result', star.result],
  ] as const;
  return (
    <div className="overflow-x-auto border-3 border-ink">
      <table className="nb-table text-sm">
        <caption className="sr-only">STAR breakdown</caption>
        <thead>
          <tr>
            <th scope="col">Part</th>
            <th scope="col">Score</th>
            <th scope="col">Why</th>
          </tr>
        </thead>
        <tbody>
          {parts.map(([name, part]) => (
            <tr key={name}>
              <th scope="row" className="text-left font-bold">
                {name}
              </th>
              <td className="font-mono font-bold">{part.score}/10</td>
              <td>
                {part.justification}
                {part.quote && <q className="mt-1 block text-xs italic">{part.quote}</q>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function QuestionAccordion({ questions }: { questions: QuestionReport[] }) {
  return (
    <section aria-labelledby="questions-heading">
      <h2 id="questions-heading" className="nb-heading text-2xl">
        Question by question
      </h2>
      <div className="mt-4 space-y-4">
        {questions.map((q) => (
          <details key={q.position} className="nb-card group">
            <summary className="flex cursor-pointer flex-wrap items-center gap-3 p-4">
              <span className="nb-badge bg-ink text-paper">Q{q.position + 1}</span>
              <span className="font-bold">{q.title}</span>
              <DifficultyBadge difficulty={q.difficulty} />
              <span className="ml-auto font-mono font-bold">{q.turnScore !== undefined ? `${q.turnScore}/10` : q.answered === false ? 'Not answered' : '—'}</span>
            </summary>
            <div className="space-y-4 border-t-3 border-ink p-4">
              {q.difficultyReason && (
                <p className="text-sm font-medium">
                  <span className="font-bold">Why this difficulty: </span>
                  {q.difficultyReason}
                </p>
              )}
              <TagList tags={q.tags} />
              <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                <div className="border-2 border-ink p-2">
                  <dt className="font-mono text-xs font-bold uppercase">Hints</dt>
                  <dd className="font-bold">{q.hintsUsed}</dd>
                </div>
                <div className="border-2 border-ink p-2">
                  <dt className="font-mono text-xs font-bold uppercase">Follow-ups</dt>
                  <dd className="font-bold">{q.followUps}</dd>
                </div>
                {q.code && (
                  <div className="col-span-2 border-2 border-ink p-2">
                    <dt className="font-mono text-xs font-bold uppercase">Tests</dt>
                    <dd className="font-bold">
                      {q.code.executed ? `${q.code.passed}/${q.code.total} passed · ${humanize(q.code.verdict)}` : 'Not executed (mock executor)'}
                      {' · '}
                      {LANGUAGE_LABEL[q.code.language as keyof typeof LANGUAGE_LABEL] ?? q.code.language}
                    </dd>
                  </div>
                )}
              </dl>
              {q.keyMissedPoints && q.keyMissedPoints.length > 0 && (
                <div>
                  <h3 className="font-bold">Key points you missed</h3>
                  <ul className="mt-1 list-disc pl-5 font-medium">
                    {q.keyMissedPoints.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                </div>
              )}
              {q.star && (
                <div>
                  <h3 className="mb-2 font-bold">STAR breakdown</h3>
                  <StarTable star={q.star} />
                </div>
              )}
              {q.modelAnswerMd && (
                <div className="border-3 border-ink bg-paper p-4">
                  <h3 className="font-bold">Model answer</h3>
                  <Markdown>{q.modelAnswerMd}</Markdown>
                </div>
              )}
            </div>
          </details>
        ))}
      </div>
    </section>
  );
}

function absoluteUrl(url: string): string {
  return url.startsWith('/') ? `${window.location.origin}${url}` : url;
}

function SharePanel({ sessionId, links }: { sessionId: string; links: ShareLink[] }) {
  const queryClient = useQueryClient();
  const [days, setDays] = useState(7);
  const [created, setCreated] = useState<ShareLink | null>(null);
  const [copied, setCopied] = useState(false);
  const [confirmRevoke, setConfirmRevoke] = useState(false);

  const create = useMutation({
    mutationFn: () => sessionsApi.share(sessionId, days),
    onSuccess: (link) => {
      setCreated(link);
      setCopied(false);
      void queryClient.invalidateQueries({ queryKey: ['report', sessionId] });
    },
  });
  const revoke = useMutation({
    mutationFn: () => sessionsApi.revokeShares(sessionId),
    onSuccess: () => {
      setCreated(null);
      setConfirmRevoke(false);
      void queryClient.invalidateQueries({ queryKey: ['report', sessionId] });
    },
  });

  const activeLinks = links.filter((l) => !l.revokedAt && new Date(l.expiresAt).getTime() > Date.now());

  async function copy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <section aria-labelledby="share-heading" className="nb-card p-6">
      <h2 id="share-heading" className="nb-heading text-2xl">
        Share this report
      </h2>
      <p className="mt-1 text-sm font-medium text-muted">
        Shared reports show your first name, scores, strengths and gaps only — no transcript, evidence or integrity notes.
      </p>
      <form
        className="mt-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <div>
          <label htmlFor="share-days" className="nb-label">
            Link expires after
          </label>
          <select id="share-days" className="nb-input w-auto" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[1, 3, 7, 14, 30].map((d) => (
              <option key={d} value={d}>
                {d} {d === 1 ? 'day' : 'days'}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className={buttonClass.primary} disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create share link'}
        </button>
        {activeLinks.length > 0 && (
          <button type="button" className={buttonClass.danger} onClick={() => setConfirmRevoke(true)}>
            Revoke all links
          </button>
        )}
      </form>
      {create.isError && (
        <div className="mt-3">
          <Alert>{describeError(create.error)}</Alert>
        </div>
      )}
      {revoke.isError && (
        <div className="mt-3">
          <Alert>{describeError(revoke.error)}</Alert>
        </div>
      )}
      {created?.url && (
        <div className="nb-accent mt-4 bg-nb-yellow p-4">
          <p className="font-bold">Your link (shown only once — copy it now):</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <label htmlFor="share-url" className="sr-only">
              Share link
            </label>
            <input id="share-url" readOnly value={absoluteUrl(created.url)} className="nb-input flex-1 font-mono text-xs" onFocus={(e) => e.target.select()} />
            <button type="button" className={`${buttonClass.ink} nb-btn-sm`} onClick={() => void copy(absoluteUrl(created.url!))}>
              {copied ? 'Copied ✓' : 'Copy'}
            </button>
          </div>
          <p className="mt-1 text-xs font-bold">Expires {formatDateTime(created.expiresAt)}</p>
          <span className="sr-only" aria-live="polite">
            {copied ? 'Link copied to clipboard' : ''}
          </span>
        </div>
      )}
      {links.length > 0 && (
        <ul className="mt-4 space-y-1 text-sm">
          {links.map((l) => (
            <li key={l.id} className="font-mono text-xs">
              Created {formatDate(l.createdAt)} · {l.viewCount} views ·{' '}
              {l.revokedAt ? 'revoked' : new Date(l.expiresAt).getTime() < Date.now() ? 'expired' : `expires ${formatDate(l.expiresAt)}`}
            </li>
          ))}
        </ul>
      )}
      <ConfirmDialog
        open={confirmRevoke}
        title="Revoke all share links?"
        confirmLabel="Revoke"
        busy={revoke.isPending}
        onConfirm={() => revoke.mutate()}
        onCancel={() => setConfirmRevoke(false)}
      >
        Anyone with a link will no longer be able to open this report.
      </ConfirmDialog>
    </section>
  );
}

function PendingEvaluation({ evaluation, sessionId }: { evaluation: Evaluation | undefined; sessionId: string }) {
  const queryClient = useQueryClient();
  const retry = useMutation({
    mutationFn: () => sessionsApi.retryEvaluation(sessionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['report', sessionId] }),
  });
  if (evaluation?.status === 'FAILED') {
    return (
      <section className="nb-accent bg-nb-red p-6" aria-labelledby="eval-failed-heading">
        <h2 id="eval-failed-heading" className="nb-heading text-2xl">
          The evaluation failed
        </h2>
        <p className="mt-2 font-medium">
          {evaluation.error ?? 'The AI evaluator could not produce a valid report.'} (attempt {evaluation.attempts})
        </p>
        <button type="button" className={`${buttonClass.ink} mt-4`} disabled={retry.isPending} onClick={() => retry.mutate()}>
          {retry.isPending ? 'Retrying…' : 'Retry evaluation'}
        </button>
        {retry.isError && <p className="mt-2 font-bold">{describeError(retry.error)}</p>}
      </section>
    );
  }
  return (
    <section className="nb-accent bg-nb-violet p-6" aria-labelledby="eval-pending-heading" aria-busy="true">
      <h2 id="eval-pending-heading" className="nb-heading text-2xl">
        Evaluating your interview…
      </h2>
      <p className="mt-2 font-medium" role="status">
        Status: {evaluation ? EVALUATION_LABEL[evaluation.status] : 'Queued'}. This page updates automatically.
      </p>
    </section>
  );
}

export function ReportPage() {
  const { id = '' } = useParams();
  const report = useQuery({
    queryKey: ['report', id],
    queryFn: () => sessionsApi.report(id),
    refetchInterval: (query) => (query.state.error || finished(query.state.data) ? false : POLL_MS),
  });

  if (report.isPending) return <PageSpinner label="Loading the report" />;
  if (report.isError) {
    const error = report.error;
    if (error instanceof ApiError && error.status === 409) {
      return <StatusPage code="409" title="No report for this interview" message="This interview was abandoned, so it wasn't evaluated." color="bg-nb-orange" />;
    }
    if (error instanceof ApiError && error.status === 404) {
      return <StatusPage code="404" title="Report not found" message="This interview doesn't exist or isn't yours." />;
    }
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <Alert>{describeError(error)}</Alert>
      </div>
    );
  }

  const { session, evaluation, transcript } = report.data;
  const done = evaluation?.status === 'SUCCEEDED';

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader
        title="Interview report"
        kicker="Report"
        kickerColor="bg-nb-green"
        description={
          <span className="flex flex-wrap items-center gap-2">
            <ModeBadge mode={session.mode} />
            {session.targetRole && <span>{ROLE_LABEL[session.targetRole]}</span>}
            {session.company && <span>· {session.company}</span>}
            <span>· {formatDateTime(session.endedAt ?? session.createdAt)}</span>
          </span>
        }
        actions={
          <Link to={`/interviews/new?mode=${session.mode}`} className={buttonClass.primary}>
            Practise again
          </Link>
        }
      />

      {!done ? (
        <PendingEvaluation evaluation={evaluation} sessionId={id} />
      ) : (
        <>
          <ScoreHero title="Overall score" score={evaluation.overallScore} confidence={evaluation.confidence} summary={evaluation.summary} />
          {evaluation.competencies && evaluation.competencies.length > 0 && (
            <CompetencySection competencies={evaluation.competencies} transcript={transcript} />
          )}
          <StrengthsGaps strengths={evaluation.strengths} gaps={evaluation.gaps} />

          {evaluation.nextSteps && evaluation.nextSteps.length > 0 && (
            <section aria-labelledby="next-heading">
              <h2 id="next-heading" className="nb-heading text-2xl">
                Your next steps
              </h2>
              <ol className="mt-4 grid gap-5 md:grid-cols-3">
                {evaluation.nextSteps.map((step, i) => (
                  <li key={i} className={`nb-accent ${accents[i % accents.length]} p-5`}>
                    <span className="nb-badge bg-white">
                      {i + 1} · {humanize(step.resourceType)}
                    </span>
                    <p className="mt-3 text-lg font-bold">{step.action}</p>
                    <p className="mt-2 text-sm font-medium">{step.why}</p>
                    {step.relatedTag && (
                      <Link to={`/questions?tag=${encodeURIComponent(step.relatedTag)}`} className="mt-3 inline-block text-sm font-bold underline">
                        Practise #{step.relatedTag}
                      </Link>
                    )}
                  </li>
                ))}
              </ol>
            </section>
          )}

          {evaluation.perQuestion && evaluation.perQuestion.length > 0 && <QuestionAccordion questions={evaluation.perQuestion} />}

          {evaluation.integrity && evaluation.integrity.length > 0 && (
            <section aria-labelledby="integrity-heading" className="nb-card-sm p-5">
              <h2 id="integrity-heading" className="nb-heading text-xl">
                Session context notes
              </h2>
              <p className="mt-1 text-sm font-medium text-muted">
                Only you can see these. They're signals recorded during the session (like large pastes or switching tabs), shown as
                context for the scores — not as accusations, and they don't change your score on their own.
              </p>
              <ul className="mt-3 space-y-1 text-sm">
                {evaluation.integrity.map((note, i) => (
                  <li key={i}>
                    <span className="nb-badge bg-nb-blue">{humanize(note.type)}</span> {note.detail}
                  </li>
                ))}
              </ul>
            </section>
          )}

          <SharePanel sessionId={id} links={report.data.shareLinks ?? []} />

          {evaluation.rubric && (
            <p className="font-mono text-xs text-muted">
              Rubric {evaluation.rubric.key} v{evaluation.rubric.version}
              {evaluation.evaluator && ` · evaluated by ${evaluation.evaluator.provider}/${evaluation.evaluator.model} (${evaluation.evaluator.promptVersion})`}
            </p>
          )}
        </>
      )}

      {transcript.length > 0 && (
        <details className="nb-card">
          <summary className="cursor-pointer p-4 font-bold">Full transcript ({transcript.length} turns)</summary>
          <ol className="space-y-3 border-t-3 border-ink p-4">
            {transcript.map((t) => (
              <li key={t.id} id={`turn-${t.seq}`} className={`border-2 border-ink p-3 ${t.role === 'CANDIDATE' ? 'bg-nb-yellow/40' : t.role === 'SYSTEM' ? 'border-dashed' : ''}`}>
                <p className="font-mono text-xs font-bold uppercase">
                  #{t.seq} · {t.role === 'CANDIDATE' ? 'You' : humanize(t.role)} · {humanize(t.kind)}
                </p>
                {t.role === 'CANDIDATE' || t.format === 'TEXT' ? <p className="mt-1 whitespace-pre-wrap">{t.content}</p> : <Markdown>{t.content}</Markdown>}
              </li>
            ))}
          </ol>
        </details>
      )}
    </div>
  );
}

export default ReportPage;
