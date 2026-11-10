import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogueApi, questionsApi } from '../../api/endpoints';
import type { Difficulty, QuestionType } from '../../api/models';
import { DifficultyBadge, TagList } from '../../components/ui/badges';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { buttonClass } from '../../components/ui/styles';
import { QUESTION_TYPE_LABEL } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';

const PAGE_SIZE = 20;

export function QuestionsPage() {
  const [params, setParams] = useSearchParams();
  const type = (params.get('type') ?? '') as QuestionType | '';
  const difficulty = (params.get('difficulty') ?? '') as Difficulty | '';
  const tag = params.get('tag') ?? '';
  const company = params.get('company') ?? '';
  const q = params.get('q') ?? '';
  const page = Math.max(1, Number(params.get('page')) || 1);
  const [draft, setDraft] = useState(q);

  const tags = useQuery({ queryKey: ['tags'], queryFn: questionsApi.tags, staleTime: 10 * 60 * 1000 });
  const companies = useQuery({ queryKey: ['companies'], queryFn: catalogueApi.companies, staleTime: 60 * 60 * 1000 });
  const results = useQuery({
    queryKey: ['questions', type, difficulty, tag, company, q, page],
    queryFn: () => questionsApi.search({ type, difficulty, tag, company, q, page, pageSize: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  }

  function onSearch(event: FormEvent) {
    event.preventDefault();
    update({ q: draft.trim() });
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <PageHeader title="Question bank" kicker="Questions" kickerColor="bg-nb-pink" description="Browse published questions by type, difficulty, topic and company." />

      <form role="search" onSubmit={onSearch} className="nb-card grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-6 lg:items-end">
        <div className="lg:col-span-2">
          <label htmlFor="q-search" className="nb-label">
            Search
          </label>
          <input id="q-search" type="search" className="nb-input" value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={100} />
        </div>
        <div>
          <label htmlFor="q-type" className="nb-label">
            Type
          </label>
          <select id="q-type" className="nb-input" value={type} onChange={(e) => update({ type: e.target.value })}>
            <option value="">All types</option>
            {(Object.keys(QUESTION_TYPE_LABEL) as QuestionType[]).map((t) => (
              <option key={t} value={t}>
                {QUESTION_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="q-difficulty" className="nb-label">
            Difficulty
          </label>
          <select id="q-difficulty" className="nb-input" value={difficulty} onChange={(e) => update({ difficulty: e.target.value })}>
            <option value="">Any</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </div>
        <div>
          <label htmlFor="q-tag" className="nb-label">
            Topic
          </label>
          <select id="q-tag" className="nb-input" value={tag} onChange={(e) => update({ tag: e.target.value })}>
            <option value="">Any topic</option>
            {tag && !(tags.data ?? []).includes(tag) && <option value={tag}>{tag}</option>}
            {(tags.data ?? []).map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="q-company" className="nb-label">
            Company
          </label>
          <select id="q-company" className="nb-input" value={company} onChange={(e) => update({ company: e.target.value })}>
            <option value="">Any company</option>
            {(companies.data ?? []).map((c) => (
              <option key={c.id} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex gap-2 lg:col-span-6">
          <button type="submit" className={buttonClass.primary}>
            Search
          </button>
          <button
            type="button"
            className={buttonClass.secondary}
            onClick={() => {
              setDraft('');
              setParams(new URLSearchParams(), { replace: true });
            }}
          >
            Clear filters
          </button>
        </div>
      </form>

      {results.isError && <Alert>{describeError(results.error)}</Alert>}

      <ul className="grid gap-4 md:grid-cols-2" aria-busy={results.isFetching}>
        {results.data?.items.map((item) => (
          <li key={item.id} className="nb-card flex flex-col gap-2 p-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="nb-badge bg-nb-blue">{QUESTION_TYPE_LABEL[item.type]}</span>
              <DifficultyBadge difficulty={item.difficulty} />
              {item.companies.map((c) => (
                <span key={c} className="nb-badge bg-nb-violet">
                  {c}
                </span>
              ))}
            </div>
            <h2 className="nb-heading text-xl">
              <Link to={`/questions/${encodeURIComponent(item.slug)}`} className="hover:underline">
                {item.title}
              </Link>
            </h2>
            <TagList tags={item.tags} />
          </li>
        ))}
      </ul>
      {results.data && results.data.items.length === 0 && (
        <p className="nb-card p-6 text-center font-bold">No questions match these filters.</p>
      )}
      <Pagination page={page} pageSize={PAGE_SIZE} total={results.data?.total} noun="questions" onPage={(p) => update({ page: String(p) })} />
    </div>
  );
}
