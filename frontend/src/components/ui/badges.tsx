import type { Difficulty, InterviewMode, SessionStatus } from '../../api/models';
import { DIFFICULTY_COLOR, MODE_COLOR, MODE_LABEL, STATUS_COLOR, STATUS_LABEL, humanize } from '../../lib/labels';

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty | string | undefined }) {
  if (!difficulty) return null;
  const color = DIFFICULTY_COLOR[difficulty as Difficulty] ?? 'bg-nb-blue';
  return <span className={`nb-badge ${color}`}>{humanize(difficulty)}</span>;
}

export function ModeBadge({ mode }: { mode: InterviewMode }) {
  return <span className={`nb-badge ${MODE_COLOR[mode] ?? 'bg-nb-blue'}`}>{MODE_LABEL[mode] ?? humanize(mode)}</span>;
}

export function StatusBadge({ status }: { status: SessionStatus }) {
  return <span className={`nb-badge ${STATUS_COLOR[status] ?? 'bg-nb-blue'}`}>{STATUS_LABEL[status] ?? humanize(status)}</span>;
}

export function TagList({ tags, label = 'Tags' }: { tags: string[] | undefined; label?: string }) {
  if (!tags || tags.length === 0) return null;
  return (
    <ul aria-label={label} className="flex flex-wrap gap-1.5">
      {tags.map((tag) => (
        <li key={tag} className="border-2 border-ink bg-surface px-1.5 font-mono text-xs font-bold">
          #{tag}
        </li>
      ))}
    </ul>
  );
}
