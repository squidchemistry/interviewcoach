import { useRef, type KeyboardEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../components/ui/PageHeader';
import { AdminAiUsageTab } from './AdminAiUsageTab';
import { AdminEmbeddingsTab } from './AdminEmbeddingsTab';
import { AdminQuestionsTab } from './AdminQuestionsTab';
import { AdminRubricsTab } from './AdminRubricsTab';
import { AdminUsersPage } from './AdminUsersPage';

const TABS = [
  { id: 'users', label: 'Users', render: () => <AdminUsersPage /> },
  { id: 'questions', label: 'Questions', render: () => <AdminQuestionsTab /> },
  { id: 'rubrics', label: 'Rubrics', render: () => <AdminRubricsTab /> },
  { id: 'ai-usage', label: 'AI usage', render: () => <AdminAiUsageTab /> },
  { id: 'embeddings', label: 'Embeddings', render: () => <AdminEmbeddingsTab /> },
] as const;

/** Admin panel: WAI-ARIA tabs (arrow keys move between tabs), selected tab kept in the URL. */
export function AdminPage() {
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab');
  const current = TABS.find((t) => t.id === requested) ?? TABS[0];
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  function select(id: string) {
    setParams({ tab: id }, { replace: true });
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const index = TABS.findIndex((t) => t.id === current.id);
    let next = index;
    if (event.key === 'ArrowRight') next = (index + 1) % TABS.length;
    else if (event.key === 'ArrowLeft') next = (index - 1 + TABS.length) % TABS.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = TABS.length - 1;
    else return;
    event.preventDefault();
    const tab = TABS[next]!;
    select(tab.id);
    refs.current[tab.id]?.focus();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <PageHeader title="Admin panel" kicker="Admin" kickerColor="bg-nb-violet" description="Manage users, the question bank, rubrics and AI usage." />
      <div role="tablist" aria-label="Admin sections" className="flex flex-wrap gap-2 border-b-3 border-ink pb-3">
        {TABS.map((tab) => {
          const selected = tab.id === current.id;
          return (
            <button
              key={tab.id}
              ref={(el) => {
                refs.current[tab.id] = el;
              }}
              id={`tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => select(tab.id)}
              onKeyDown={onKeyDown}
              className={`nb-btn nb-btn-sm ${selected ? 'nb-btn-primary' : 'nb-btn-secondary'}`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      <div role="tabpanel" id={`panel-${current.id}`} aria-labelledby={`tab-${current.id}`}>
        {current.render()}
      </div>
    </div>
  );
}

export default AdminPage;
