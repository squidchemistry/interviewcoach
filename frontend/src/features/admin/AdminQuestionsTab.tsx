import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { adminContentApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { AdminQuestion, Difficulty, QuestionStatus, QuestionType, UpsertQuestionRequest } from '../../api/models';
import { DifficultyBadge } from '../../components/ui/badges';
import { Alert } from '../../components/ui/form';
import { Pagination } from '../../components/ui/Pagination';
import { buttonClass } from '../../components/ui/styles';
import { QUESTION_TYPE_LABEL, formatDate } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

const PAGE_SIZE = 20;
const STATUSES: QuestionStatus[] = ['PUBLISHED', 'DRAFT', 'ARCHIVED'];
const STATUS_COLOR: Record<QuestionStatus, string> = { PUBLISHED: 'bg-nb-green', DRAFT: 'bg-nb-yellow', ARCHIVED: 'bg-nb-red' };

/** Starting `detail` JSON per type, mirroring QuestionDetails.{Dsa,Lld,Behavioural}Detail. */
const DETAIL_TEMPLATES: Record<QuestionType, unknown> = {
  DSA: {
    functionName: 'solve',
    params: [{ name: 'nums', type: 'int[]' }],
    returnType: 'int',
    comparator: 'EXACT',
    expectedTimeComplexity: 'O(n)',
    expectedSpaceComplexity: 'O(1)',
    testCases: [{ input: { nums: [1, 2, 3] }, expected: 6, sample: true }],
    hints: ['', '', ''],
    expectedPoints: [],
    referenceSolutionPython: '',
  },
  LLD: { requirements: [''], focusAreas: [''], referenceDesignMd: '', referenceMermaid: '', expectedPoints: [] },
  BEHAVIOURAL: { competency: '', leadershipPrinciple: '', whatGoodLooksLike: '', followUpProbes: [], expectedPoints: [] },
};

const list = (text: string) => text.split(',').map((t) => t.trim()).filter(Boolean);

interface EditorState {
  id: string | null;
  version: number | undefined;
  type: QuestionType;
  slug: string;
  title: string;
  bodyMd: string;
  difficulty: Difficulty;
  status: QuestionStatus;
  tags: string;
  companies: string;
  detail: string;
}

function fromQuestion(q: AdminQuestion): EditorState {
  return {
    id: q.id,
    version: q.version,
    type: q.type,
    slug: q.slug,
    title: q.title,
    bodyMd: q.bodyMd,
    difficulty: q.difficulty,
    status: q.status,
    tags: q.tags.join(', '),
    companies: q.companies.join(', '),
    detail: JSON.stringify(q.detail, null, 2),
  };
}

function blank(type: QuestionType = 'DSA'): EditorState {
  return {
    id: null,
    version: undefined,
    type,
    slug: '',
    title: '',
    bodyMd: '',
    difficulty: 'EASY',
    status: 'DRAFT',
    tags: '',
    companies: '',
    detail: JSON.stringify(DETAIL_TEMPLATES[type], null, 2),
  };
}

function QuestionEditor({ initial, onDone }: { initial: EditorState; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(initial);
  const [jsonError, setJsonError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (body: UpsertQuestionRequest) => (form.id ? adminContentApi.updateQuestion(form.id, body) : adminContentApi.createQuestion(body)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] });
      onDone();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    let detail: unknown;
    try {
      detail = JSON.parse(form.detail);
    } catch (err) {
      setJsonError(`Detail is not valid JSON: ${err instanceof Error ? err.message : 'parse error'}`);
      return;
    }
    if (!detail || typeof detail !== 'object' || Array.isArray(detail)) {
      setJsonError('Detail must be a JSON object.');
      return;
    }
    setJsonError(null);
    save.mutate({
      type: form.type,
      slug: form.slug.trim(),
      title: form.title.trim(),
      bodyMd: form.bodyMd,
      difficulty: form.difficulty,
      status: form.status,
      tags: list(form.tags),
      companies: list(form.companies),
      detail,
      ...(form.version !== undefined ? { expectedVersion: form.version } : {}),
    });
  }

  const fieldErrors = save.error instanceof ApiError ? Object.entries(save.error.fieldErrors()) : [];
  const set = (patch: Partial<EditorState>) => setForm((f) => ({ ...f, ...patch }));

  return (
    <form onSubmit={submit} className="nb-card space-y-4 p-5" aria-labelledby="editor-heading">
      <h3 id="editor-heading" className="nb-heading text-xl">
        {form.id ? `Edit “${initial.title}”` : 'New question'}
      </h3>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <label htmlFor="qe-type" className="nb-label">
            Type
          </label>
          <select
            id="qe-type"
            className="nb-input"
            value={form.type}
            disabled={form.id !== null}
            onChange={(e) => {
              const type = e.target.value as QuestionType;
              set({ type, detail: JSON.stringify(DETAIL_TEMPLATES[type], null, 2) });
            }}
          >
            {(Object.keys(QUESTION_TYPE_LABEL) as QuestionType[]).map((t) => (
              <option key={t} value={t}>
                {QUESTION_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="qe-difficulty" className="nb-label">
            Difficulty
          </label>
          <select id="qe-difficulty" className="nb-input" value={form.difficulty} onChange={(e) => set({ difficulty: e.target.value as Difficulty })}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </div>
        <div>
          <label htmlFor="qe-status" className="nb-label">
            Status
          </label>
          <select id="qe-status" className="nb-input" value={form.status} onChange={(e) => set({ status: e.target.value as QuestionStatus })}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="qe-slug" className="nb-label">
            Slug (kebab-case)
          </label>
          <input id="qe-slug" className="nb-input font-mono" required maxLength={80} value={form.slug} onChange={(e) => set({ slug: e.target.value })} />
        </div>
      </div>
      <div>
        <label htmlFor="qe-title" className="nb-label">
          Title
        </label>
        <input id="qe-title" className="nb-input" required minLength={3} maxLength={160} value={form.title} onChange={(e) => set({ title: e.target.value })} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="qe-tags" className="nb-label">
            Tags (comma-separated, max 6)
          </label>
          <input id="qe-tags" className="nb-input font-mono" value={form.tags} onChange={(e) => set({ tags: e.target.value })} />
        </div>
        <div>
          <label htmlFor="qe-companies" className="nb-label">
            Company slugs (comma-separated, max 5)
          </label>
          <input id="qe-companies" className="nb-input font-mono" value={form.companies} onChange={(e) => set({ companies: e.target.value })} />
        </div>
      </div>
      <div>
        <label htmlFor="qe-body" className="nb-label">
          Body (Markdown)
        </label>
        <textarea id="qe-body" className="nb-input min-h-40 font-mono text-sm" required maxLength={20000} value={form.bodyMd} onChange={(e) => set({ bodyMd: e.target.value })} />
      </div>
      <div>
        <label htmlFor="qe-detail" className="nb-label">
          Detail (JSON, validated per type by the server)
        </label>
        <textarea
          id="qe-detail"
          className="nb-input min-h-64 font-mono text-xs"
          spellCheck={false}
          aria-invalid={jsonError ? true : undefined}
          aria-describedby={jsonError ? 'qe-detail-error' : undefined}
          value={form.detail}
          onChange={(e) => set({ detail: e.target.value })}
        />
        {jsonError && (
          <p id="qe-detail-error" className="mt-1.5 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">
            {jsonError}
          </p>
        )}
      </div>
      {save.isError && (
        <Alert>
          {describeError(save.error)}
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
        <button type="submit" className={buttonClass.primary} disabled={save.isPending}>
          {save.isPending ? 'Saving…' : form.id ? 'Save changes' : 'Create question'}
        </button>
        <button type="button" className={buttonClass.secondary} onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  );
}

export function AdminQuestionsTab() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [q, setQ] = useState('');
  const [type, setType] = useState<QuestionType | ''>('');
  const [status, setStatus] = useState<QuestionStatus | ''>('');
  const [page, setPage] = useState(1);
  const [editing, setEditing] = useState<EditorState | null>(null);

  const questions = useQuery({
    queryKey: ['admin', 'questions', q, type, status, page],
    queryFn: () => adminContentApi.searchQuestions({ q, type, status, page, pageSize: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });
  const changeStatus = useMutation({
    mutationFn: ({ id, next }: { id: string; next: QuestionStatus }) => adminContentApi.changeStatus(id, next),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] }),
  });
  const load = useMutation({ mutationFn: adminContentApi.getQuestion, onSuccess: (full) => setEditing(fromQuestion(full)) });

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="nb-heading text-2xl">Questions</h2>
        <button type="button" className={buttonClass.primary} onClick={() => setEditing(blank())}>
          + New question
        </button>
      </div>

      {editing && <QuestionEditor key={editing.id ?? 'new'} initial={editing} onDone={() => setEditing(null)} />}

      <form
        role="search"
        className="flex flex-col gap-4 sm:flex-row sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(1);
          setQ(draft);
        }}
      >
        <div className="flex-1">
          <label htmlFor="aq-search" className="nb-label">
            Search title or slug
          </label>
          <input id="aq-search" type="search" className="nb-input" value={draft} onChange={(e) => setDraft(e.target.value)} />
        </div>
        <div>
          <label htmlFor="aq-type" className="nb-label">
            Type
          </label>
          <select
            id="aq-type"
            className="nb-input"
            value={type}
            onChange={(e) => {
              setPage(1);
              setType(e.target.value as QuestionType | '');
            }}
          >
            <option value="">All</option>
            {(Object.keys(QUESTION_TYPE_LABEL) as QuestionType[]).map((t) => (
              <option key={t} value={t}>
                {QUESTION_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="aq-status" className="nb-label">
            Status
          </label>
          <select
            id="aq-status"
            className="nb-input"
            value={status}
            onChange={(e) => {
              setPage(1);
              setStatus(e.target.value as QuestionStatus | '');
            }}
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className={buttonClass.secondary}>
          Search
        </button>
      </form>

      {questions.isError && <Alert>{describeError(questions.error)}</Alert>}
      {changeStatus.isError && <Alert>{describeError(changeStatus.error)}</Alert>}
      {load.isError && <Alert>{describeError(load.error)}</Alert>}

      <div className="nb-card overflow-x-auto">
        <table className="nb-table min-w-full text-sm">
          <caption className="sr-only">Questions</caption>
          <thead>
            <tr>
              <th scope="col">Title</th>
              <th scope="col">Type</th>
              <th scope="col">Difficulty</th>
              <th scope="col">Status</th>
              <th scope="col">Updated</th>
              <th scope="col">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {questions.data?.items.map((item) => (
              <tr key={item.id}>
                <td>
                  <span className="font-bold">{item.title}</span>
                  <span className="block font-mono text-xs">{item.slug} · v{item.version}</span>
                </td>
                <td>{QUESTION_TYPE_LABEL[item.type]}</td>
                <td>
                  <DifficultyBadge difficulty={item.difficulty} />
                </td>
                <td>
                  <label htmlFor={`status-${item.id}`} className="sr-only">
                    Status of {item.title}
                  </label>
                  <select
                    id={`status-${item.id}`}
                    className={`nb-input w-auto py-1 text-xs font-bold text-[#0a0a0a] ${STATUS_COLOR[item.status]}`}
                    value={item.status}
                    disabled={changeStatus.isPending}
                    onChange={(e) => changeStatus.mutate({ id: item.id, next: e.target.value as QuestionStatus })}
                  >
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {s.toLowerCase()}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="whitespace-nowrap font-mono text-xs">{formatDate(item.updatedAt)}</td>
                <td className="text-right">
                  <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} disabled={load.isPending} onClick={() => load.mutate(item.id)}>
                    Edit<span className="sr-only">: {item.title}</span>
                  </button>
                </td>
              </tr>
            ))}
            {questions.data && questions.data.items.length === 0 && (
              <tr>
                <td colSpan={6} className="py-6 text-center font-medium">
                  No questions match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pagination page={page} pageSize={PAGE_SIZE} total={questions.data?.total} noun="questions" onPage={setPage} />
    </div>
  );
}
