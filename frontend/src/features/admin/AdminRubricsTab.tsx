import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { adminContentApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { Rubric } from '../../api/models';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { formatDateTime } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

const KEYS = ['DSA', 'LLD', 'BEHAVIOURAL', 'RESUME_DEEP_DIVE'] as const;

function NewVersionForm({ base, onDone }: { base: Rubric | undefined; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [key, setKey] = useState<string>(base?.key ?? 'DSA');
  const [definition, setDefinition] = useState(base ? JSON.stringify(base.definition, null, 2) : '{\n  \n}');
  const [changelog, setChangelog] = useState('');
  const [jsonError, setJsonError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: adminContentApi.createRubric,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'rubrics'] });
      onDone();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    let parsed: unknown;
    try {
      parsed = JSON.parse(definition);
    } catch (err) {
      setJsonError(`Definition is not valid JSON: ${err instanceof Error ? err.message : 'parse error'}`);
      return;
    }
    setJsonError(null);
    create.mutate({ key, definition: parsed, ...(changelog.trim() ? { changelog: changelog.trim() } : {}) });
  }

  const fieldErrors = create.error instanceof ApiError ? Object.entries(create.error.fieldErrors()) : [];

  return (
    <form onSubmit={submit} className="nb-card space-y-4 p-5" aria-labelledby="rubric-form-heading">
      <h3 id="rubric-form-heading" className="nb-heading text-xl">
        New rubric version
      </h3>
      <div className="grid gap-4 sm:grid-cols-[200px_1fr]">
        <div>
          <label htmlFor="rb-key" className="nb-label">
            Round
          </label>
          <select id="rb-key" className="nb-input" value={key} onChange={(e) => setKey(e.target.value)}>
            {KEYS.map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="rb-changelog" className="nb-label">
            Changelog
          </label>
          <input id="rb-changelog" className="nb-input" maxLength={500} value={changelog} onChange={(e) => setChangelog(e.target.value)} />
        </div>
      </div>
      <div>
        <label htmlFor="rb-definition" className="nb-label">
          Definition (JSON)
        </label>
        <textarea
          id="rb-definition"
          className="nb-input min-h-72 font-mono text-xs"
          spellCheck={false}
          value={definition}
          aria-invalid={jsonError ? true : undefined}
          aria-describedby={jsonError ? 'rb-definition-error' : undefined}
          onChange={(e) => setDefinition(e.target.value)}
        />
        {jsonError && (
          <p id="rb-definition-error" className="mt-1.5 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">
            {jsonError}
          </p>
        )}
      </div>
      {create.isError && (
        <Alert>
          {describeError(create.error)}
          {fieldErrors.length > 0 && (
            <ul className="mt-1 list-disc pl-5">
              {fieldErrors.map(([field, message]) => (
                <li key={field}>
                  <span className="font-mono">{field}</span>: {message}
                </li>
              ))}
            </ul>
          )}
        </Alert>
      )}
      <div className="flex gap-3">
        <button type="submit" className={buttonClass.primary} disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create (inactive) version'}
        </button>
        <button type="button" className={buttonClass.secondary} onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  );
}

export function AdminRubricsTab() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState<{ base: Rubric | undefined } | null>(null);
  const rubrics = useQuery({ queryKey: ['admin', 'rubrics'], queryFn: adminContentApi.rubrics });
  const activate = useMutation({
    mutationFn: adminContentApi.activateRubric,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'rubrics'] }),
  });

  const sorted = [...(rubrics.data ?? [])].sort((a, b) => a.key.localeCompare(b.key) || b.version - a.version);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="nb-heading text-2xl">Rubrics</h2>
        <button type="button" className={buttonClass.primary} onClick={() => setCreating({ base: undefined })}>
          + New version
        </button>
      </div>
      <p className="text-sm font-medium text-muted">New sessions use the active version; existing reports keep the version they were scored with.</p>
      {creating && <NewVersionForm key={creating.base?.id ?? 'blank'} base={creating.base} onDone={() => setCreating(null)} />}
      {rubrics.isError && <Alert>{describeError(rubrics.error)}</Alert>}
      {activate.isError && <Alert>{describeError(activate.error)}</Alert>}
      <div className="nb-card overflow-x-auto">
        <table className="nb-table min-w-full text-sm">
          <caption className="sr-only">Rubric versions</caption>
          <thead>
            <tr>
              <th scope="col">Round</th>
              <th scope="col">Version</th>
              <th scope="col">Status</th>
              <th scope="col">Changelog</th>
              <th scope="col">Created</th>
              <th scope="col">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((r) => (
              <tr key={r.id}>
                <td className="font-bold">{r.key}</td>
                <td className="font-mono">v{r.version}</td>
                <td>{r.active ? <span className="nb-badge bg-nb-green">Active</span> : <span className="nb-badge bg-surface">Inactive</span>}</td>
                <td>{r.changelog ?? '—'}</td>
                <td className="whitespace-nowrap font-mono text-xs">{formatDateTime(r.createdAt)}</td>
                <td className="space-x-2 whitespace-nowrap text-right">
                  <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} onClick={() => setCreating({ base: r })}>
                    Copy as new<span className="sr-only">: {r.key} v{r.version}</span>
                  </button>
                  {!r.active && (
                    <button type="button" className={`${buttonClass.primary} nb-btn-sm`} disabled={activate.isPending} onClick={() => activate.mutate(r.id)}>
                      Activate<span className="sr-only">: {r.key} v{r.version}</span>
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {rubrics.data && rubrics.data.length === 0 && (
              <tr>
                <td colSpan={6} className="py-6 text-center font-medium">
                  No rubrics yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
