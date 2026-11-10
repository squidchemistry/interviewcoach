import { lazy, Suspense, useState } from 'react';
import { sessionsApi } from '../../../api/endpoints';
import type { CodeRunResponse } from '../../../api/models';
import type { CodeLanguage } from '../../../api/types';
import { Alert } from '../../../components/ui/form';
import { buttonClass } from '../../../components/ui/styles';
import { LANGUAGE_LABEL, humanize } from '../../../lib/labels';
import { describeError } from '../../auth/errorMessages';

const CodeEditor = lazy(() => import('./CodeEditor'));

const LANGUAGES = Object.keys(LANGUAGE_LABEL) as CodeLanguage[];

function show(value: unknown): string {
  if (value === undefined || value === null) return '—';
  return typeof value === 'string' ? value : JSON.stringify(value);
}

export function RunResults({ result }: { result: CodeRunResponse }) {
  const passedAll = result.executed && result.passed === result.total;
  return (
    <div className="space-y-3" aria-live="polite">
      {!result.executed && (
        <Alert tone="info">
          Code execution is disabled on this server (CODE_EXECUTOR=mock); tests were not run.
        </Alert>
      )}
      {result.executed && (
        <p className={`nb-accent inline-block px-3 py-1 font-bold ${passedAll ? 'bg-nb-green' : 'bg-nb-red'}`}>
          {result.kind === 'SUBMIT' ? 'Submit' : 'Run'}: {humanize(result.verdict)} · {result.passed}/{result.total} passed
          {result.runtimeMs !== undefined && ` · ${result.runtimeMs} ms`}
        </p>
      )}
      {result.compileOutput && (
        <pre className="max-h-48 overflow-auto border-2 border-ink bg-paper p-2 font-mono text-xs">{result.compileOutput}</pre>
      )}
      {result.tests.length > 0 && (
        <div className="overflow-x-auto border-3 border-ink">
          <table className="nb-table text-xs">
            <caption className="sr-only">Test results</caption>
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Verdict</th>
                <th scope="col">Input</th>
                <th scope="col">Expected</th>
                <th scope="col">Actual</th>
                <th scope="col">Time</th>
              </tr>
            </thead>
            <tbody>
              {result.tests.map((t) => (
                <tr key={t.index}>
                  <td className="font-mono">
                    {t.index + 1}
                    {t.sample ? '' : ' (hidden)'}
                  </td>
                  <td>
                    <span className={`nb-badge ${t.verdict === 'PASSED' || t.verdict === 'ACCEPTED' ? 'bg-nb-green' : 'bg-nb-red'}`}>
                      {humanize(t.verdict)}
                    </span>
                  </td>
                  <td className="max-w-40 break-all font-mono">{show(t.input)}</td>
                  <td className="max-w-40 break-all font-mono">{show(t.expected)}</td>
                  <td className="max-w-40 break-all font-mono">
                    {show(t.actual)}
                    {t.stderr && <span className="block text-[#b00020]">{t.stderr}</span>}
                  </td>
                  <td className="font-mono">{t.timeMs !== undefined ? `${t.timeMs} ms` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export function CodePanel({
  sessionId,
  language,
  onLanguage,
  source,
  onSource,
  onPaste,
  disabled,
}: {
  sessionId: string;
  language: CodeLanguage;
  onLanguage: (language: CodeLanguage) => void;
  source: string;
  onSource: (source: string) => void;
  onPaste: (size: number) => void;
  disabled: boolean;
}) {
  const [running, setRunning] = useState<'run' | 'submit' | null>(null);
  const [result, setResult] = useState<CodeRunResponse | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function execute(kind: 'run' | 'submit') {
    setRunning(kind);
    setError(null);
    try {
      const body = { language, source };
      setResult(kind === 'run' ? await sessionsApi.runCode(sessionId, body) : await sessionsApi.submitCode(sessionId, body));
    } catch (err) {
      setError(err);
    } finally {
      setRunning(null);
    }
  }

  return (
    <section aria-labelledby="code-heading" className="nb-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b-3 border-ink p-3">
        <h2 id="code-heading" className="nb-heading text-lg">
          Code
        </h2>
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor="code-language" className="sr-only">
            Language
          </label>
          <select
            id="code-language"
            className="nb-input w-auto py-1"
            value={language}
            onChange={(e) => onLanguage(e.target.value as CodeLanguage)}
          >
            {LANGUAGES.map((l) => (
              <option key={l} value={l}>
                {LANGUAGE_LABEL[l]}
              </option>
            ))}
          </select>
          <button
            type="button"
            className={`${buttonClass.secondary} nb-btn-sm`}
            disabled={disabled || running !== null || !source.trim()}
            onClick={() => void execute('run')}
          >
            {running === 'run' ? 'Running…' : '▶ Run samples'}
          </button>
          <button
            type="button"
            className={`${buttonClass.ink} nb-btn-sm`}
            disabled={disabled || running !== null || !source.trim()}
            onClick={() => void execute('submit')}
          >
            {running === 'submit' ? 'Submitting…' : 'Submit all tests'}
          </button>
        </div>
      </div>
      <div className="border-b-3 border-ink">
        <Suspense fallback={<p className="p-4 font-mono text-sm font-bold">Loading editor…</p>}>
          <CodeEditor language={language} value={source} onChange={onSource} onPaste={onPaste} />
        </Suspense>
      </div>
      <div className="space-y-3 p-3">
        <p className="text-xs font-medium text-muted">Your latest code is attached to every answer you send.</p>
        {error !== null && <Alert>{describeError(error)}</Alert>}
        {result && <RunResults result={result} />}
      </div>
    </section>
  );
}
