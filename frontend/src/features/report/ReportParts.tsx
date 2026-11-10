import type { Confidence } from '../../api/models';
import { formatScore } from '../../lib/labels';

const CONFIDENCE_COLOR: Record<Confidence, string> = { HIGH: 'bg-nb-green', MEDIUM: 'bg-nb-yellow', LOW: 'bg-nb-orange' };

function scoreColor(score0to100: number | undefined): string {
  if (score0to100 === undefined) return 'bg-nb-blue';
  if (score0to100 >= 75) return 'bg-nb-green';
  if (score0to100 >= 50) return 'bg-nb-yellow';
  return 'bg-nb-orange';
}

export function ScoreHero({ score, confidence, summary, title }: { score: number | undefined; confidence: Confidence | undefined; summary: string | undefined; title: string }) {
  return (
    <section aria-labelledby="score-heading" className="grid gap-6 md:grid-cols-[auto_1fr]">
      <div className={`nb-accent ${scoreColor(score)} flex min-w-48 flex-col items-center justify-center px-8 py-6`}>
        <h2 id="score-heading" className="font-mono text-xs font-bold uppercase">
          {title}
        </h2>
        <p className="nb-heading text-7xl" aria-describedby="score-scale">
          {formatScore(score)}
        </p>
        <p id="score-scale" className="font-mono text-xs font-bold">
          out of 100
        </p>
        {confidence && (
          <p className={`nb-badge mt-3 ${CONFIDENCE_COLOR[confidence] ?? 'bg-white'}`}>
            {confidence.toLowerCase()} confidence
          </p>
        )}
      </div>
      {summary && (
        <div className="nb-card p-6">
          <h2 className="nb-heading text-xl">Summary</h2>
          <p className="mt-2 text-lg font-medium leading-relaxed">{summary}</p>
        </div>
      )}
    </section>
  );
}

/** One horizontal bar per competency (score 0–10), pure CSS, with a low-confidence marker. */
export function CompetencyBar({ label, score, lowConfidence, weight }: { label: string; score: number; lowConfidence: boolean; weight?: number | undefined }) {
  const pct = Math.max(0, Math.min(100, score * 10));
  return (
    <div>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="font-bold">
          {label}
          {weight !== undefined && <span className="ml-2 font-mono text-xs text-muted">weight {Math.round(weight * 100)}%</span>}
        </span>
        <span className="font-mono font-bold">
          {score.toFixed(1)}/10
          {lowConfidence && <span className="nb-badge ml-2 bg-nb-orange">low confidence</span>}
        </span>
      </div>
      <div
        role="meter"
        aria-label={`${label}${lowConfidence ? ' (low confidence)' : ''}`}
        aria-valuemin={0}
        aria-valuemax={10}
        aria-valuenow={score}
        className="mt-1 h-6 border-3 border-ink bg-surface"
      >
        <div
          className={`h-full border-r-3 border-ink ${lowConfidence ? 'bg-[repeating-linear-gradient(45deg,var(--orange)_0_8px,var(--yellow)_8px_16px)]' : score >= 7.5 ? 'bg-nb-green' : score >= 5 ? 'bg-nb-yellow' : 'bg-nb-orange'}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

export function StrengthsGaps({ strengths, gaps }: { strengths: string[] | undefined; gaps: string[] | undefined }) {
  if (!strengths?.length && !gaps?.length) return null;
  return (
    <div className="grid gap-6 md:grid-cols-2">
      <section aria-labelledby="strengths-heading" className="nb-accent bg-nb-green p-5">
        <h2 id="strengths-heading" className="nb-heading text-2xl">
          Strengths
        </h2>
        <ul className="mt-3 list-disc space-y-1.5 pl-5 font-medium">
          {(strengths ?? []).map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      </section>
      <section aria-labelledby="gaps-heading" className="nb-accent bg-nb-pink p-5">
        <h2 id="gaps-heading" className="nb-heading text-2xl">
          Gaps to work on
        </h2>
        <ul className="mt-3 list-disc space-y-1.5 pl-5 font-medium">
          {(gaps ?? []).map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
