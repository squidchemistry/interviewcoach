import type { Difficulty, EvaluationStatus, InterviewMode, QuestionType, SessionStatus } from '../api/models';

export const MODE_LABEL: Record<InterviewMode, string> = {
  DSA: 'DSA coding',
  LLD: 'Low-level design',
  BEHAVIOURAL: 'Behavioural',
  RESUME_DEEP_DIVE: 'Resume deep-dive',
};

export const MODE_COLOR: Record<InterviewMode, string> = {
  DSA: 'bg-nb-yellow',
  LLD: 'bg-nb-blue',
  BEHAVIOURAL: 'bg-nb-pink',
  RESUME_DEEP_DIVE: 'bg-nb-green',
};

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  DSA: 'DSA',
  LLD: 'LLD',
  BEHAVIOURAL: 'Behavioural',
};

export const DIFFICULTY_COLOR: Record<Difficulty, string> = {
  EASY: 'bg-nb-green',
  MEDIUM: 'bg-nb-orange',
  HARD: 'bg-nb-red',
};

export const STATUS_LABEL: Record<SessionStatus, string> = {
  CREATED: 'Created',
  IN_PROGRESS: 'In progress',
  EVALUATING: 'Evaluating',
  COMPLETED: 'Completed',
  ABANDONED: 'Abandoned',
};

export const STATUS_COLOR: Record<SessionStatus, string> = {
  CREATED: 'bg-nb-blue',
  IN_PROGRESS: 'bg-nb-yellow',
  EVALUATING: 'bg-nb-violet',
  COMPLETED: 'bg-nb-green',
  ABANDONED: 'bg-nb-red',
};

export const EVALUATION_LABEL: Record<EvaluationStatus, string> = {
  PENDING: 'Queued',
  RUNNING: 'Scoring',
  SUCCEEDED: 'Ready',
  FAILED: 'Failed',
};

export const ROLE_LABEL = { SDE_1: 'SDE-1', SDE_2: 'SDE-2' } as const;

export const LANGUAGE_LABEL = { PYTHON: 'Python', JAVA: 'Java', CPP: 'C++', JAVASCRIPT: 'JavaScript' } as const;

export function formatDate(iso: string | undefined | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(iso: string | undefined | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/** 0–100 overall score, rounded for display. */
export function formatScore(score: number | undefined | null): string {
  return score === undefined || score === null ? '—' : String(Math.round(score));
}

/** ₹ amount from paise, e.g. 49900 → "₹499". */
export function formatRupees(paise: number): string {
  const rupees = paise / 100;
  return `₹${rupees.toLocaleString('en-IN', { minimumFractionDigits: rupees % 1 === 0 ? 0 : 2, maximumFractionDigits: 2 })}`;
}

export function humanize(value: string): string {
  const text = value.replace(/[_-]+/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}
