import { useEffect, useRef } from 'react';
import type { Turn } from '../../../api/models';
import { Markdown } from '../../../components/ui/Markdown';
import { LANGUAGE_LABEL } from '../../../lib/labels';
import type { StreamingTurn } from './roomState';

const KIND_LABEL: Record<string, string> = {
  QUESTION: 'Question',
  FOLLOW_UP: 'Follow-up',
  HINT: 'Hint',
  ANSWER: 'Answer',
  CODE_RUN: 'Code run',
  SYSTEM_NOTICE: 'Notice',
};

function Attachments({ metadata }: { metadata: Record<string, unknown> | undefined }) {
  if (!metadata) return null;
  const code = typeof metadata.code === 'string' ? metadata.code : null;
  const language = typeof metadata.language === 'string' ? metadata.language : null;
  const mermaid = typeof metadata.mermaid === 'string' ? metadata.mermaid : null;
  return (
    <>
      {code && (
        <details className="mt-2 border-2 border-[#0a0a0a] bg-white/60">
          <summary className="cursor-pointer px-2 py-1 font-mono text-xs font-bold">
            Attached code{language ? ` (${LANGUAGE_LABEL[language as keyof typeof LANGUAGE_LABEL] ?? language})` : ''}
          </summary>
          <pre className="max-h-64 overflow-auto p-2 font-mono text-xs">{code}</pre>
        </details>
      )}
      {mermaid && (
        <details className="mt-2 border-2 border-[#0a0a0a] bg-white/60">
          <summary className="cursor-pointer px-2 py-1 font-mono text-xs font-bold">Attached diagram (Mermaid)</summary>
          <pre className="max-h-64 overflow-auto p-2 font-mono text-xs">{mermaid}</pre>
        </details>
      )}
    </>
  );
}

function TurnBubble({ turn }: { turn: Turn }) {
  if (turn.role === 'SYSTEM') {
    return (
      <li className="mx-auto max-w-[90%] border-2 border-dashed border-ink px-3 py-1.5 text-center font-mono text-xs font-bold">
        {turn.content}
      </li>
    );
  }
  const candidate = turn.role === 'CANDIDATE';
  const hintLevel = turn.kind === 'HINT' ? Number((turn.metadata as { hintLevel?: unknown } | undefined)?.hintLevel ?? 0) : 0;
  return (
    <li className={`flex ${candidate ? 'justify-end' : 'justify-start'}`}>
      <article
        aria-label={`${candidate ? 'You' : 'Interviewer'}${KIND_LABEL[turn.kind] ? `, ${KIND_LABEL[turn.kind]}` : ''}`}
        className={`nb-accent max-w-[92%] px-4 py-3 sm:max-w-[85%] ${
          candidate ? 'bg-nb-yellow' : turn.kind === 'HINT' ? 'bg-nb-green' : 'bg-nb-blue'
        }`}
        style={{ boxShadow: '4px 4px 0 0 var(--shadow)' }}
      >
        <header className="mb-1 flex items-center gap-2 font-mono text-[0.7rem] font-bold uppercase">
          <span>{candidate ? 'You' : 'Interviewer'}</span>
          {turn.kind === 'HINT' && <span className="nb-badge bg-white">Hint{hintLevel ? ` L${hintLevel}` : ''}</span>}
          {turn.kind === 'FOLLOW_UP' && <span className="nb-badge bg-white">Follow-up</span>}
          {turn.inputMode === 'VOICE' && <span className="nb-badge bg-white">Voice</span>}
        </header>
        {candidate || turn.format === 'TEXT' ? (
          <p className="whitespace-pre-wrap break-words">{turn.content}</p>
        ) : (
          <Markdown>{turn.content}</Markdown>
        )}
        <Attachments metadata={turn.metadata} />
      </article>
    </li>
  );
}

export function Transcript({
  turns,
  streaming,
  pendingAnswer,
  processing,
}: {
  turns: Turn[];
  streaming: StreamingTurn[];
  pendingAnswer: string | null;
  processing: boolean;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const streamedChars = streaming.reduce((n, s) => n + s.text.length, 0);

  useEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [turns.length, streamedChars, pendingAnswer]);

  const thinking = processing && streaming.length === 0 && !pendingAnswer;

  return (
    <div ref={scroller} className="max-h-[60vh] min-h-64 overflow-y-auto p-4">
      <ol className="space-y-4" aria-label="Transcript">
        {turns.map((turn) => (
          <TurnBubble key={turn.id} turn={turn} />
        ))}
        {pendingAnswer && (
          <li className="flex justify-end">
            <article aria-label="You, sending" className="nb-accent max-w-[85%] bg-nb-yellow/70 px-4 py-3" style={{ boxShadow: '4px 4px 0 0 var(--shadow)' }}>
              <header className="mb-1 font-mono text-[0.7rem] font-bold uppercase">You · sending…</header>
              <p className="whitespace-pre-wrap break-words">{pendingAnswer}</p>
            </article>
          </li>
        )}
      </ol>
      <div aria-live="polite" aria-atomic="false" aria-busy={streaming.length > 0} className="mt-4 space-y-4">
        {streaming.map((s) => (
          <article
            key={s.turnId}
            aria-label="Interviewer is responding"
            data-testid="streaming-bubble"
            className="nb-accent max-w-[85%] bg-nb-blue px-4 py-3"
            style={{ boxShadow: '4px 4px 0 0 var(--shadow)' }}
          >
            <header className="mb-1 font-mono text-[0.7rem] font-bold uppercase">Interviewer · typing</header>
            <p className="whitespace-pre-wrap break-words">
              {s.text}
              <span aria-hidden="true" className="ml-0.5 inline-block h-4 w-2 animate-pulse bg-[#0a0a0a] align-middle" />
            </p>
          </article>
        ))}
        {thinking && (
          <p className="font-mono text-xs font-bold" role="status">
            Interviewer is thinking…
          </p>
        )}
      </div>
    </div>
  );
}
