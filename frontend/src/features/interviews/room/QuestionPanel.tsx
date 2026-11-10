import type { SessionQuestion } from '../../../api/models';
import { DifficultyBadge, TagList } from '../../../components/ui/badges';
import { Markdown } from '../../../components/ui/Markdown';
import { humanize } from '../../../lib/labels';

function json(value: unknown): string {
  return JSON.stringify(value);
}

export function QuestionPanel({ question, total }: { question: SessionQuestion | undefined; total: number }) {
  if (!question) {
    return (
      <section aria-labelledby="question-heading" className="nb-card p-5">
        <h2 id="question-heading" className="nb-heading text-xl">
          Waiting for the first question…
        </h2>
        <p className="mt-2 font-medium text-muted">The interviewer will introduce the problem in a moment.</p>
      </section>
    );
  }
  const dsa = question.context?.dsa;
  const lld = question.context?.lld;
  const behavioural = question.context?.behavioural;
  return (
    <section aria-labelledby="question-heading" className="nb-card p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="nb-badge bg-ink text-paper">
          Q{question.position + 1}/{total}
        </span>
        <DifficultyBadge difficulty={question.difficulty} />
      </div>
      <h2 id="question-heading" className="nb-heading mt-3 text-2xl">
        {question.title}
      </h2>
      {question.difficultyReason && (
        <p className="mt-2 border-l-4 border-ink bg-nb-violet/40 px-2 py-1 text-xs font-semibold">
          <span className="font-mono uppercase">Why this difficulty: </span>
          {question.difficultyReason}
        </p>
      )}
      <div className="mt-3">
        <TagList tags={question.tags} />
      </div>
      <Markdown className="mt-3">{question.promptMd}</Markdown>

      {dsa && (
        <div className="mt-4 space-y-3 text-sm">
          <p className="font-mono text-xs">
            <span className="font-bold">Signature: </span>
            {dsa.functionName}({dsa.params.map((p) => `${p.name}: ${p.type}`).join(', ')}) → {dsa.returnType}
          </p>
          {dsa.samples.length > 0 && (
            <div>
              <h3 className="font-bold">Sample tests</h3>
              <ol className="mt-1 space-y-2">
                {dsa.samples.map((s, i) => (
                  <li key={i} className="border-2 border-ink bg-paper p-2 font-mono text-xs">
                    <div>
                      <span className="font-bold">in:</span> {json(s.input)}
                    </div>
                    <div>
                      <span className="font-bold">out:</span> {json(s.expected)}
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          )}
          <p className="text-xs font-medium text-muted">+ {dsa.hiddenTestCount} hidden tests run on Submit.</p>
        </div>
      )}
      {lld && (
        <div className="mt-4 grid gap-3 text-sm">
          <div>
            <h3 className="font-bold">Requirements</h3>
            <ul className="mt-1 list-disc pl-5">
              {lld.requirements.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </div>
          <div>
            <h3 className="font-bold">Focus areas</h3>
            <ul className="mt-1 list-disc pl-5">
              {lld.focusAreas.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {behavioural && (
        <p className="mt-4 text-sm font-medium">
          <span className="font-bold">Competency: </span>
          {humanize(behavioural.competency)}
          {behavioural.leadershipPrinciple && <> · {behavioural.leadershipPrinciple}</>}
        </p>
      )}
      {question.context?.projectName && (
        <p className="mt-4 text-sm font-medium">
          <span className="font-bold">From your resume: </span>
          {question.context.projectName}
        </p>
      )}
    </section>
  );
}
