import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { catalogueApi, progressApi, questionsApi, resumesApi, sessionsApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { CreateSessionRequest, Difficulty, InterviewMode } from '../../api/models';
import type { TargetRole } from '../../api/types';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { buttonClass } from '../../components/ui/styles';
import { ROLE_LABEL } from '../../lib/labels';
import { useAuth } from '../auth/authContext';
import { describeError } from '../auth/errorMessages';

interface ModeOption {
  mode: InterviewMode;
  title: string;
  description: string;
  tag: string;
  color: string;
  min: number;
  max: number;
  defaultCount: number;
}

const MODE_OPTIONS: ModeOption[] = [
  { mode: 'DSA', title: 'DSA coding round', description: 'Solve problems in a real editor, run tests, explain complexity.', tag: 'CODE', color: 'bg-nb-yellow', min: 1, max: 3, defaultCount: 2 },
  { mode: 'LLD', title: 'Low-level design', description: 'Design classes and relationships, sketch a diagram, defend trade-offs.', tag: 'DESIGN', color: 'bg-nb-blue', min: 1, max: 3, defaultCount: 2 },
  { mode: 'BEHAVIOURAL', title: 'Behavioural (STAR)', description: 'Tell real stories, scored on Situation, Task, Action and Result.', tag: 'STORY', color: 'bg-nb-pink', min: 3, max: 6, defaultCount: 4 },
  { mode: 'RESUME_DEEP_DIVE', title: 'Resume deep-dive', description: 'Get grilled on the projects and choices in your own resume.', tag: 'YOU', color: 'bg-nb-green', min: 3, max: 6, defaultCount: 4 },
];

const PACK_MODES: Record<InterviewMode, string | null> = { DSA: 'DSA', LLD: 'LLD', BEHAVIOURAL: 'BEHAVIOURAL', RESUME_DEEP_DIVE: null };

function isMode(value: string | null): value is InterviewMode {
  return MODE_OPTIONS.some((m) => m.mode === value);
}

function errorFor(error: unknown): { message: string; billing: boolean; resume: boolean } {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'QUOTA_EXCEEDED':
        return { message: "You've used all your free interviews this month. Upgrade to Pro for unlimited practice.", billing: true, resume: false };
      case 'PRO_REQUIRED':
        return { message: 'That option (voice or a Pro pack) needs the Pro plan.', billing: true, resume: false };
      case 'RESUME_REQUIRED':
      case 'RESUME_NOT_PARSED':
        return { message: 'A resume deep-dive needs a parsed resume. Upload one first.', billing: false, resume: true };
      case 'NO_QUESTIONS_AVAILABLE':
        return { message: 'No questions match these options yet. Try another difficulty, company or pack.', billing: false, resume: false };
      default:
        if (error.status === 402) return { message: describeError(error), billing: true, resume: false };
    }
  }
  return { message: describeError(error), billing: false, resume: false };
}

export function NewInterviewPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const initialMode = params.get('mode');
  const isPro = user?.plan === 'PRO';

  const [mode, setMode] = useState<InterviewMode>(isMode(initialMode) ? initialMode : 'DSA');
  const option = MODE_OPTIONS.find((m) => m.mode === mode)!;
  const [count, setCount] = useState<number>(option.defaultCount);
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [role, setRole] = useState<TargetRole>(user?.profile?.targetRole ?? 'SDE_1');
  const [company, setCompany] = useState('');
  const [pack, setPack] = useState('');
  const [voice, setVoice] = useState(false);
  const [resumeId, setResumeId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const usage = useQuery({ queryKey: ['usage'], queryFn: progressApi.usage });
  const companies = useQuery({ queryKey: ['companies'], queryFn: catalogueApi.companies, staleTime: 60 * 60 * 1000 });
  const packs = useQuery({ queryKey: ['packs'], queryFn: questionsApi.packs, staleTime: 10 * 60 * 1000 });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: resumesApi.list, enabled: mode === 'RESUME_DEEP_DIVE' });

  const parsedResumes = (resumes.data ?? []).filter((r) => r.status === 'PARSED');
  const activeResume = parsedResumes.find((r) => r.active);
  const packMode = PACK_MODES[mode];
  const modePacks = (packs.data ?? []).filter((p) => packMode !== null && p.mode === packMode);

  function chooseMode(next: InterviewMode) {
    setMode(next);
    setCount(MODE_OPTIONS.find((m) => m.mode === next)!.defaultCount);
    setPack('');
    setError(null);
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const body: CreateSessionRequest = { mode, questionCount: count, targetRole: role };
    if (difficulty) body.startDifficulty = difficulty;
    if (company && mode !== 'RESUME_DEEP_DIVE') body.company = company;
    if (pack) body.packSlug = pack;
    if (voice) body.voice = true;
    if (mode === 'RESUME_DEEP_DIVE' && (resumeId || activeResume)) body.resumeId = resumeId || activeResume!.id;
    try {
      const session = await sessionsApi.create(body);
      try {
        await sessionsApi.start(session.id);
      } catch {
        // The room shows a Start button for sessions still in CREATED, so this is recoverable there.
      }
      navigate(`/interviews/${session.id}`);
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  const failure = error ? errorFor(error) : null;
  const limit = usage.data?.interviewLimit;
  const exhausted = limit !== undefined && limit !== null && usage.data!.interviewsStarted >= limit;

  return (
    <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader
        title="Start a mock interview"
        kicker="Practice"
        description="Pick a round, tune the options, and the AI interviewer takes it from there."
      />

      {usage.data && (
        <p className={`nb-accent inline-block px-4 py-2 font-bold ${exhausted ? 'bg-nb-red' : 'bg-nb-green'}`} role="status">
          {limit === undefined || limit === null
            ? `Pro plan · unlimited interviews (${usage.data.interviewsStarted} started this month)`
            : `${usage.data.interviewsStarted} of ${limit} free interviews used this month`}
          {exhausted && (
            <>
              {' · '}
              <Link to="/billing" className="underline">
                Upgrade to Pro
              </Link>
            </>
          )}
        </p>
      )}

      <form onSubmit={onSubmit} className="space-y-8" aria-describedby={failure ? 'create-error' : undefined}>
        <fieldset>
          <legend className="nb-heading mb-4 text-2xl">1 · Choose a round</legend>
          <div className="grid gap-5 sm:grid-cols-2">
            {MODE_OPTIONS.map((m) => {
              const selected = m.mode === mode;
              return (
                <label
                  key={m.mode}
                  className={`nb-card block cursor-pointer p-5 transition-transform focus-within:outline-3 focus-within:outline-dashed focus-within:outline-offset-4 ${
                    selected ? `${m.color} -translate-x-1 -translate-y-1 text-[#0a0a0a]` : ''
                  }`}
                >
                  <input
                    type="radio"
                    name="mode"
                    value={m.mode}
                    checked={selected}
                    onChange={() => chooseMode(m.mode)}
                    className="sr-only"
                  />
                  <span className="flex items-center justify-between gap-2">
                    <span className={`nb-badge ${selected ? 'bg-white' : m.color}`}>{m.tag}</span>
                    {selected && <span className="font-mono text-xs font-bold">✓ SELECTED</span>}
                  </span>
                  <span className="nb-heading mt-3 block text-2xl">{m.title}</span>
                  <span className="mt-2 block font-medium">{m.description}</span>
                </label>
              );
            })}
          </div>
        </fieldset>

        <fieldset className="nb-card p-6">
          <legend className="nb-heading bg-surface px-2 text-2xl">2 · Options</legend>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            <div>
              <label htmlFor="question-count" className="nb-label">
                Questions ({option.min}–{option.max})
              </label>
              <select id="question-count" className="nb-input" value={count} onChange={(e) => setCount(Number(e.target.value))}>
                {Array.from({ length: option.max - option.min + 1 }, (_, i) => option.min + i).map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="start-difficulty" className="nb-label">
                Start difficulty
              </label>
              <select
                id="start-difficulty"
                className="nb-input"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}
              >
                <option value="">Based on role ({role === 'SDE_2' ? 'Medium' : 'Easy'})</option>
                <option value="EASY">Easy</option>
                <option value="MEDIUM">Medium</option>
                <option value="HARD">Hard</option>
              </select>
            </div>
            <div>
              <label htmlFor="target-role" className="nb-label">
                Target role
              </label>
              <select id="target-role" className="nb-input" value={role} onChange={(e) => setRole(e.target.value as TargetRole)}>
                <option value="SDE_1">{ROLE_LABEL.SDE_1}</option>
                <option value="SDE_2">{ROLE_LABEL.SDE_2}</option>
              </select>
            </div>
            {mode !== 'RESUME_DEEP_DIVE' && (
              <div>
                <label htmlFor="company" className="nb-label">
                  Company focus
                </label>
                <select id="company" className="nb-input" value={company} onChange={(e) => setCompany(e.target.value)}>
                  <option value="">Any company</option>
                  {(companies.data ?? []).map((c) => (
                    <option key={c.id} value={c.slug}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {packMode !== null && (
              <div>
                <label htmlFor="pack" className="nb-label">
                  Question pack
                </label>
                <select id="pack" className="nb-input" value={pack} onChange={(e) => setPack(e.target.value)}>
                  <option value="">General question bank</option>
                  {modePacks.map((p) => (
                    <option key={p.slug} value={p.slug} disabled={p.pro && !isPro}>
                      {p.name} ({p.questionCount} questions){p.pro ? ' · ★ Pro' : ''}
                    </option>
                  ))}
                </select>
                {modePacks.some((p) => p.pro) && !isPro && (
                  <p className="mt-1 text-xs font-medium text-muted">
                    ★ Pro packs need the <Link to="/billing" className="nb-link">Pro plan</Link>.
                  </p>
                )}
              </div>
            )}
            {mode === 'RESUME_DEEP_DIVE' && (
              <div>
                <label htmlFor="resume" className="nb-label">
                  Resume
                </label>
                {resumes.isPending ? (
                  <p className="font-medium">Loading resumes…</p>
                ) : parsedResumes.length === 0 ? (
                  <p className="font-medium">
                    No parsed resume yet. <Link to="/resume" className="nb-link">Upload your resume</Link> first.
                  </p>
                ) : (
                  <select id="resume" className="nb-input" value={resumeId || activeResume?.id || ''} onChange={(e) => setResumeId(e.target.value)}>
                    {!activeResume && <option value="">Choose a resume</option>}
                    {parsedResumes.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.filename}
                        {r.active ? ' (active)' : ''}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            )}
          </div>

          <div className="mt-6 flex items-start gap-3">
            <input
              id="voice"
              type="checkbox"
              className="nb-check mt-0.5"
              checked={voice}
              disabled={!isPro}
              onChange={(e) => setVoice(e.target.checked)}
              aria-describedby="voice-hint"
            />
            <div>
              <label htmlFor="voice" className="font-bold">
                Voice mode <span className="nb-badge bg-nb-violet">★ Pro</span>
              </label>
              <p id="voice-hint" className="text-sm font-medium text-muted">
                The interviewer speaks its questions and you can dictate answers (uses your browser's speech features).
                {!isPro && (
                  <>
                    {' '}
                    <Link to="/billing" className="nb-link">Upgrade to enable</Link>.
                  </>
                )}
              </p>
            </div>
          </div>
        </fieldset>

        {failure && (
          <div id="create-error">
            <Alert>
              {failure.message}{' '}
              {failure.billing && (
                <Link to="/billing" className="underline">
                  See plans
                </Link>
              )}
              {failure.resume && (
                <Link to="/resume" className="underline">
                  Go to resume
                </Link>
              )}
            </Alert>
          </div>
        )}

        <button
          type="submit"
          className={`${buttonClass.primary} px-8 py-4 text-lg`}
          disabled={submitting || (mode === 'RESUME_DEEP_DIVE' && !resumes.isPending && parsedResumes.length === 0)}
        >
          {submitting ? 'Setting up your interview…' : 'Start interview →'}
        </button>
      </form>
    </div>
  );
}
