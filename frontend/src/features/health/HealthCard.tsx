import { useHealth } from '../../api/health';
import { buttonClass } from '../../components/ui/styles';

const COMPONENT_LABELS: Record<string, string> = {
  db: 'Database',
  redis: 'Redis',
  pgvector: 'Vector search (pgvector)',
  readinessState: 'Application',
};

function StatusDot({ status }: { status: string }) {
  const color = status === 'UP' ? 'bg-nb-green' : status === 'UNREACHABLE' ? 'bg-surface' : 'bg-nb-red';
  return <span aria-hidden="true" className={`inline-block h-3.5 w-3.5 shrink-0 border-2 border-ink ${color}`} />;
}

/** Live readiness of the API and its dependencies, refreshed every 15 seconds. */
export function HealthCard() {
  const { data, error, isPending, isFetching, refetch } = useHealth();

  let summary: string;
  let overall: string;
  if (isPending) {
    summary = 'Checking system status…';
    overall = 'UNKNOWN';
  } else if (error || !data) {
    summary = 'API unreachable';
    overall = 'UNREACHABLE';
  } else if (data.status === 'UP') {
    summary = 'All systems operational';
    overall = 'UP';
  } else {
    summary = 'Degraded: some dependencies are down';
    overall = data.status;
  }

  return (
    <section aria-labelledby="system-status-heading" className="nb-card p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="system-status-heading" className="nb-heading text-lg">
          System status
        </h2>
        <button type="button" onClick={() => void refetch()} disabled={isFetching} className={`${buttonClass.secondary} nb-btn-sm`}>
          {isFetching ? 'Checking…' : 'Refresh'}
        </button>
      </div>

      <p role="status" aria-live="polite" className="mt-3 flex items-center gap-2 font-bold">
        <StatusDot status={overall} />
        <span>{summary}</span>
      </p>

      {data && Object.keys(data.components).length > 0 && (
        <ul className="mt-4 grid gap-2 sm:grid-cols-2" aria-label="Dependency status">
          {Object.entries(data.components).map(([name, status]) => (
            <li key={name} className="flex items-center gap-2 border-2 border-ink px-3 py-1.5 text-sm font-medium">
              <StatusDot status={status} />
              <span>{COMPONENT_LABELS[name] ?? name}</span>
              <span className="ml-auto font-mono text-xs font-bold">{status}</span>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p className="mt-3 text-sm font-medium text-muted">
          Start the backend with <code className="font-mono">docker compose up</code> and this card will update automatically.
        </p>
      )}

      {data && <p className="mt-4 font-mono text-xs text-muted">API version {data.version}</p>}
    </section>
  );
}
