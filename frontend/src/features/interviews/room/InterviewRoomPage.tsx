import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { sessionsApi } from '../../../api/endpoints';
import { ApiError } from '../../../api/http';
import type { InputMode, SubmitAnswerRequest } from '../../../api/models';
import type { CodeLanguage } from '../../../api/types';
import { ModeBadge, StatusBadge } from '../../../components/ui/badges';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { Alert } from '../../../components/ui/form';
import { PageSpinner } from '../../../components/ui/PageSpinner';
import { StatusPage } from '../../../components/ui/StatusPage';
import { buttonClass } from '../../../components/ui/styles';
import { humanize } from '../../../lib/labels';
import { useAuth } from '../../auth/authContext';
import { describeError } from '../../auth/errorMessages';
import { AnswerBox } from './AnswerBox';
import { CodePanel } from './CodePanel';
import { DiagramPanel } from './DiagramPanel';
import { QuestionPanel } from './QuestionPanel';
import { currentQuestion } from './roomState';
import { Transcript } from './Transcript';
import { useSessionRoom } from './useSessionRoom';
import { useTelemetry } from './useTelemetry';
import { speak, speechSynthesisSupported, stopSpeaking } from './voice';

function answerError(error: unknown): string {
  if (error instanceof ApiError && (error.code === 'STATE_CONFLICT' || error.code === 'INVALID_TRANSITION')) {
    return 'The interviewer is still responding. Wait for it to finish, then send your answer.';
  }
  return describeError(error);
}

const EVAL_STEPS = ['Queued', 'Scoring against the rubric', 'Verifying evidence', 'Done'];

export function InterviewRoomPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const { state, dispatch, loadError, connection, reload } = useSessionRoom(id);
  const detail = state.detail;
  const session = detail?.session;
  const question = currentQuestion(detail);

  const [answer, setAnswer] = useState('');
  const [inputMode, setInputMode] = useState<InputMode>('TEXT');
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<'end' | 'abandon' | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  const [language, setLanguage] = useState<CodeLanguage>(user?.profile?.preferredLanguage ?? 'PYTHON');
  const [codeDrafts, setCodeDrafts] = useState<Record<string, string>>({});
  const [diagram, setDiagram] = useState('');
  const [speakTurns, setSpeakTurns] = useState(true);
  const [hinting, setHinting] = useState(false);

  const active = session?.status === 'IN_PROGRESS';
  const { recordPaste } = useTelemetry(id, active);

  // Leave for the report once the evaluation is done.
  useEffect(() => {
    if (session?.status === 'COMPLETED') {
      void queryClient.invalidateQueries({ queryKey: ['sessions'] });
      navigate(`/interviews/${id}/report`, { replace: true });
    }
  }, [session?.status, id, navigate, queryClient]);

  // Voice: read each new interviewer turn aloud.
  const spokenUpTo = useRef<number | null>(null);
  useEffect(() => {
    if (!detail) return;
    const latest = detail.transcript.at(-1);
    if (spokenUpTo.current === null) {
      spokenUpTo.current = latest?.seq ?? 0; // don't read the history on (re)load
      return;
    }
    if (!session?.voiceEnabled || !speakTurns || !latest || latest.seq <= spokenUpTo.current) return;
    spokenUpTo.current = latest.seq;
    if (latest.role === 'INTERVIEWER') speak(latest.content);
  }, [detail, session?.voiceEnabled, speakTurns]);
  useEffect(() => () => stopSpeaking(), []);

  const codeKey = `${question?.id ?? 'none'}:${language}`;
  const starter = question?.context?.dsa?.starterCode?.[language] ?? '';
  const code = codeDrafts[codeKey] ?? starter;
  const setCode = useCallback((value: string) => setCodeDrafts((d) => ({ ...d, [codeKey]: value })), [codeKey]);

  if (loadError) {
    if (loadError instanceof ApiError && loadError.status === 404) {
      return <StatusPage code="404" title="Interview not found" message="This interview doesn't exist or isn't yours." />;
    }
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <Alert>{describeError(loadError)}</Alert>
        <button type="button" className={`${buttonClass.secondary} mt-4`} onClick={() => void reload()}>
          Try again
        </button>
      </div>
    );
  }
  if (!detail || !session) return <PageSpinner label="Loading your interview" />;

  const processing = session.processing || state.streaming.length > 0;
  const total = Math.max(session.plannedQuestions, detail.questions.length);
  const hintsUsed = question?.hintsUsed ?? 0;

  async function submitAnswer() {
    if (!answer.trim() || !session) return;
    const content = answer.trim();
    const body: SubmitAnswerRequest = { clientMessageId: crypto.randomUUID(), content, inputMode };
    if (session.mode === 'DSA' && code.trim()) body.code = { language, source: code };
    if (session.mode === 'LLD' && diagram.trim()) body.diagram = { mermaid: diagram };
    setSubmitting(true);
    setActionError(null);
    dispatch({ type: 'answer.pending', content });
    try {
      const response = await sessionsApi.answer(id, body);
      dispatch({ type: 'command', response });
      setAnswer('');
      setInputMode('TEXT');
    } catch (error) {
      dispatch({ type: 'answer.failed' });
      setActionError(answerError(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function command(run: () => Promise<unknown>) {
    setActionError(null);
    try {
      const response = await run();
      if (response && typeof response === 'object' && 'status' in response) {
        dispatch({ type: 'command', response: response as never });
      }
    } catch (error) {
      setActionError(describeError(error));
    }
  }

  async function requestHint() {
    setHinting(true);
    await command(() => sessionsApi.hint(id, hintsUsed + 1));
    setHinting(false);
  }

  async function confirmAction() {
    setConfirmBusy(true);
    try {
      if (confirm === 'end') {
        const response = await sessionsApi.end(id);
        dispatch({ type: 'command', response });
        setConfirm(null);
      } else if (confirm === 'abandon') {
        await sessionsApi.abandon(id);
        void queryClient.invalidateQueries({ queryKey: ['sessions'] });
        navigate('/interviews', { replace: true });
      }
    } catch (error) {
      setActionError(describeError(error));
      setConfirm(null);
    } finally {
      setConfirmBusy(false);
    }
  }

  const isWorkspace = session.mode === 'DSA' || session.mode === 'LLD';
  const evaluation = state.evaluation ?? (detail.evaluationStatus ? { status: detail.evaluationStatus } : null);

  return (
    <div className="mx-auto max-w-[1500px] px-4 py-6 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="nb-heading text-2xl">Interview room</h1>
          <ModeBadge mode={session.mode} />
          <StatusBadge status={session.status} />
          {session.currentDifficulty && <span className="nb-badge bg-nb-orange">Now: {humanize(session.currentDifficulty)}</span>}
          {connection === 'reconnecting' && (
            <span role="status" className="nb-badge bg-nb-red">
              Reconnecting…
            </span>
          )}
        </div>
        {(session.status === 'IN_PROGRESS' || session.status === 'CREATED') && (
          <div className="flex flex-wrap gap-2">
            {session.voiceEnabled && speechSynthesisSupported() && (
              <button
                type="button"
                className={`${buttonClass.secondary} nb-btn-sm`}
                aria-pressed={speakTurns}
                onClick={() => {
                  if (speakTurns) stopSpeaking();
                  setSpeakTurns(!speakTurns);
                }}
              >
                {speakTurns ? '🔊 Voice on' : '🔇 Voice off'}
              </button>
            )}
            {session.status === 'IN_PROGRESS' && (
              <button type="button" className={`${buttonClass.ink} nb-btn-sm`} onClick={() => setConfirm('end')}>
                End interview
              </button>
            )}
            <button type="button" className={`${buttonClass.danger} nb-btn-sm`} onClick={() => setConfirm('abandon')}>
              Abandon
            </button>
          </div>
        )}
      </div>

      {session.status === 'CREATED' && (
        <div className="nb-accent mt-6 bg-nb-yellow p-6">
          <h2 className="nb-heading text-2xl">Ready when you are</h2>
          <p className="mt-1 font-medium">The interviewer will ask the first question as soon as you start.</p>
          <button type="button" className={`${buttonClass.ink} mt-4`} onClick={() => void command(() => sessionsApi.start(id))}>
            Start interview
          </button>
        </div>
      )}

      {session.status === 'ABANDONED' && (
        <div className="mt-6">
          <Alert tone="info">
            This interview was abandoned and won't be scored. <Link to="/interviews/new" className="underline">Start a new one</Link>.
          </Alert>
        </div>
      )}

      {actionError && (
        <div className="mt-4">
          <Alert>{actionError}</Alert>
        </div>
      )}
      {state.error && (
        <div className="mt-4">
          <Alert>
            {state.error.message}
            {state.error.retryable && (
              <button
                type="button"
                className={`${buttonClass.secondary} nb-btn-sm ml-3`}
                onClick={() => {
                  dispatch({ type: 'error.dismiss' });
                  void command(() => sessionsApi.retryTurn(id));
                }}
              >
                Retry
              </button>
            )}
          </Alert>
        </div>
      )}
      {state.assessment && session.status === 'IN_PROGRESS' && (
        <aside aria-label="Adaptive difficulty" role="status" className="nb-accent mt-4 bg-nb-violet px-4 py-3 text-sm font-semibold" style={{ boxShadow: '4px 4px 0 0 var(--shadow)' }}>
          <span className="font-mono text-xs uppercase">Adaptive difficulty · </span>
          Q{state.assessment.position + 1} scored {state.assessment.turnScore ?? '—'}/10
          {state.assessment.nextDifficulty && <> → next question {humanize(state.assessment.nextDifficulty)}</>}
          {state.assessment.reason && <>. {state.assessment.reason}</>}
        </aside>
      )}

      <div className={`mt-6 grid gap-6 ${isWorkspace ? 'xl:grid-cols-[minmax(260px,0.8fr)_minmax(0,1.1fr)_minmax(0,1.3fr)]' : 'lg:grid-cols-[minmax(280px,0.9fr)_minmax(0,1.6fr)]'}`}>
        <div className="space-y-4">
          <QuestionPanel question={question} total={total} />
          {session.mode === 'DSA' && question && session.status === 'IN_PROGRESS' && (
            <div className="nb-card-sm flex flex-wrap items-center justify-between gap-2 p-3">
              <span className="text-sm font-bold">Hints used: {hintsUsed}/3</span>
              <button
                type="button"
                className={`${buttonClass.secondary} nb-btn-sm`}
                disabled={hintsUsed >= 3 || processing || hinting}
                onClick={() => void requestHint()}
              >
                {hintsUsed >= 3 ? 'No hints left' : `Get hint ${hintsUsed + 1}`}
              </button>
            </div>
          )}
        </div>

        <section aria-label="Conversation" className="nb-card flex min-w-0 flex-col">
          <Transcript turns={detail.transcript} streaming={state.streaming} pendingAnswer={state.pendingAnswer} processing={processing} />
          {session.status === 'IN_PROGRESS' && (
            <AnswerBox
              value={answer}
              onChange={setAnswer}
              onSubmit={() => void submitAnswer()}
              disabled={processing || submitting}
              busy={submitting}
              disabledReason={processing ? 'Wait for the interviewer to finish' : undefined}
              voiceEnabled={session.voiceEnabled}
              onPaste={recordPaste}
              onDictated={() => setInputMode('VOICE')}
            />
          )}
        </section>

        {isWorkspace && session.status === 'IN_PROGRESS' && (
          <div className="min-w-0">
            {session.mode === 'DSA' ? (
              <>
                {!detail.codeExecutionEnabled && (
                  <div className="mb-3">
                    <Alert tone="info">Code execution is disabled on this server (CODE_EXECUTOR=mock); tests were not run.</Alert>
                  </div>
                )}
                <CodePanel
                  sessionId={id}
                  language={language}
                  onLanguage={setLanguage}
                  source={code}
                  onSource={setCode}
                  onPaste={recordPaste}
                  disabled={!question}
                />
              </>
            ) : (
              <DiagramPanel value={diagram} onChange={setDiagram} onPaste={recordPaste} disabled={false} />
            )}
          </div>
        )}
      </div>

      {session.status === 'EVALUATING' && (
        <div className="fixed inset-0 z-40 grid place-items-center bg-[#0a0a0a]/70 p-4">
          <div role="dialog" aria-modal="true" aria-labelledby="evaluating-heading" className="nb-card w-full max-w-md p-6 text-center">
            <h2 id="evaluating-heading" className="nb-heading text-3xl">
              Evaluating…
            </h2>
            <p className="mt-2 font-medium text-muted">Your answers are being scored against the rubric with evidence from your own words.</p>
            <ol className="mt-5 space-y-2 text-left" aria-live="polite">
              {EVAL_STEPS.map((step, i) => {
                const currentStep = evaluation?.status === 'SUCCEEDED' ? 3 : evaluation?.status === 'RUNNING' ? (state.evaluation?.step === 'Verifying evidence' ? 2 : 1) : 0;
                const done = i < currentStep;
                const now = i === currentStep;
                return (
                  <li key={step} className={`flex items-center gap-2 border-2 border-ink px-3 py-1.5 font-bold ${done ? 'bg-nb-green text-[#0a0a0a]' : now ? 'bg-nb-yellow text-[#0a0a0a]' : ''}`}>
                    <span aria-hidden="true">{done ? '✓' : now ? '…' : '○'}</span>
                    {step}
                    <span className="sr-only">{done ? ' (done)' : now ? ' (in progress)' : ''}</span>
                  </li>
                );
              })}
            </ol>
            {evaluation?.status === 'FAILED' && (
              <p className="mt-4 font-bold">The evaluation failed. You can retry it from the report.</p>
            )}
            <Link to={`/interviews/${id}/report`} className={`${buttonClass.secondary} mt-5`}>
              Go to the report page
            </Link>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirm === 'end'}
        title="End the interview?"
        confirmLabel="End and get feedback"
        tone="primary"
        busy={confirmBusy}
        onConfirm={() => void confirmAction()}
        onCancel={() => setConfirm(null)}
      >
        Remaining questions are skipped and your answers so far are evaluated.
      </ConfirmDialog>
      <ConfirmDialog
        open={confirm === 'abandon'}
        title="Abandon this interview?"
        confirmLabel="Abandon"
        busy={confirmBusy}
        onConfirm={() => void confirmAction()}
        onCancel={() => setConfirm(null)}
      >
        It won't be scored and you can't resume it. Abandoned interviews may still count toward your monthly quota.
      </ConfirmDialog>
    </div>
  );
}

export default InterviewRoomPage;
