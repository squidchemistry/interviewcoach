import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { questionsApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { QuestionType } from '../../api/models';
import { DifficultyBadge, TagList } from '../../components/ui/badges';
import { Alert } from '../../components/ui/form';
import { Markdown } from '../../components/ui/Markdown';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { StatusPage } from '../../components/ui/StatusPage';
import { buttonClass } from '../../components/ui/styles';
import { QUESTION_TYPE_LABEL, humanize } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

const PRACTICE_MODE: Record<QuestionType, string> = { DSA: 'DSA', LLD: 'LLD', BEHAVIOURAL: 'BEHAVIOURAL' };

export function QuestionDetailPage() {
  const { idOrSlug = '' } = useParams();
  const question = useQuery({ queryKey: ['question', idOrSlug], queryFn: () => questionsApi.get(idOrSlug) });
  const similar = useQuery({ queryKey: ['question', idOrSlug, 'similar'], queryFn: () => questionsApi.similar(idOrSlug, 5), enabled: question.isSuccess });

  if (question.isPending) return <PageSpinner label="Loading question" />;
  if (question.isError) {
    if (question.error instanceof ApiError && question.error.status === 404) {
      return <StatusPage code="404" title="Question not found" message="It may have been unpublished." />;
    }
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <Alert>{describeError(question.error)}</Alert>
      </div>
    );
  }
  const q = question.data;

  return (
    <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 sm:px-6 lg:grid-cols-[1fr_300px]">
      <article className="space-y-5">
        <Link to="/questions" className="nb-link text-sm">
          ← All questions
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <span className="nb-badge bg-nb-blue">{QUESTION_TYPE_LABEL[q.type]}</span>
          <DifficultyBadge difficulty={q.difficulty} />
          {q.companies.map((c) => (
            <span key={c} className="nb-badge bg-nb-violet">
              {c}
            </span>
          ))}
        </div>
        <h1 className="nb-heading text-3xl sm:text-4xl">{q.title}</h1>
        <TagList tags={q.tags} />
        <div className="nb-card p-6">
          <Markdown>{q.bodyMd}</Markdown>
        </div>

        {q.dsa && (
          <section aria-labelledby="signature-heading" className="nb-card space-y-4 p-6">
            <h2 id="signature-heading" className="nb-heading text-xl">
              Signature
            </h2>
            <pre className="overflow-x-auto border-2 border-ink bg-paper p-3 font-mono text-sm">
              {q.dsa.functionName}({q.dsa.params.map((p) => `${p.name}: ${p.type}`).join(', ')}) → {q.dsa.returnType}
            </pre>
            {q.dsa.samples.length > 0 && (
              <div className="overflow-x-auto">
                <table className="nb-table text-sm">
                  <caption className="mb-2 text-left font-bold">Sample tests</caption>
                  <thead>
                    <tr>
                      <th scope="col">Input</th>
                      <th scope="col">Expected</th>
                    </tr>
                  </thead>
                  <tbody>
                    {q.dsa.samples.map((s, i) => (
                      <tr key={i}>
                        <td className="break-all font-mono text-xs">{JSON.stringify(s.input)}</td>
                        <td className="break-all font-mono text-xs">{JSON.stringify(s.expected)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="text-sm font-medium text-muted">{q.dsa.hiddenTestCount} hidden tests are run when you submit in an interview.</p>
          </section>
        )}
        {q.lld && (
          <section aria-labelledby="lld-heading" className="nb-card grid gap-4 p-6 sm:grid-cols-2">
            <h2 id="lld-heading" className="sr-only">
              Design details
            </h2>
            <div>
              <h3 className="font-bold">Requirements</h3>
              <ul className="mt-1 list-disc pl-5">
                {q.lld.requirements.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </div>
            <div>
              <h3 className="font-bold">Focus areas</h3>
              <ul className="mt-1 list-disc pl-5">
                {q.lld.focusAreas.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            </div>
          </section>
        )}
        {q.behavioural && (
          <p className="nb-card-sm p-4 font-medium">
            <span className="font-bold">Competency: </span>
            {humanize(q.behavioural.competency)}
            {q.behavioural.leadershipPrinciple && <> · {q.behavioural.leadershipPrinciple}</>}
          </p>
        )}
        <Link to={`/interviews/new?mode=${PRACTICE_MODE[q.type]}`} className={buttonClass.primary}>
          Practise a {QUESTION_TYPE_LABEL[q.type]} interview →
        </Link>
      </article>

      <aside aria-labelledby="similar-heading" className="nb-card h-fit p-5">
        <h2 id="similar-heading" className="nb-heading text-xl">
          Similar questions
        </h2>
        {similar.isPending && <p className="mt-2 text-sm font-medium">Finding similar questions…</p>}
        {similar.isError && <p className="mt-2 text-sm font-medium">{describeError(similar.error)}</p>}
        {similar.data && similar.data.length === 0 && <p className="mt-2 text-sm font-medium">No similar questions yet.</p>}
        <ul className="mt-3 space-y-2">
          {similar.data?.map((s) => (
            <li key={s.question.id} className="border-2 border-ink p-2">
              <Link to={`/questions/${encodeURIComponent(s.question.slug)}`} className="font-bold hover:underline">
                {s.question.title}
              </Link>
              <div className="mt-1 flex items-center gap-2">
                <DifficultyBadge difficulty={s.question.difficulty} />
                <span className="font-mono text-xs">{Math.round(s.similarity * 100)}% similar</span>
              </div>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}
