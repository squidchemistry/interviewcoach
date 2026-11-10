import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { adminContentApi } from '../../api/endpoints';
import { Alert } from '../../components/ui/form';
import { describeError } from '../auth/errorMessages';

type GroupBy = 'day' | 'purpose' | 'model';

const pct = (x: number) => `${(x * 100).toFixed(1)}%`;

export function AdminAiUsageTab() {
  const [groupBy, setGroupBy] = useState<GroupBy>('day');
  const [days, setDays] = useState(14);
  const usage = useQuery({ queryKey: ['admin', 'ai-usage', groupBy, days], queryFn: () => adminContentApi.aiUsage(groupBy, days) });
  const rows = usage.data ?? [];
  const totals = rows.reduce(
    (t, r) => ({ calls: t.calls + r.calls, input: t.input + r.inputTokens, output: t.output + r.outputTokens }),
    { calls: 0, input: 0, output: 0 },
  );

  return (
    <div className="space-y-5">
      <h2 className="nb-heading text-2xl">AI usage</h2>
      <div className="flex flex-wrap gap-4">
        <div>
          <label htmlFor="ai-group" className="nb-label">
            Group by
          </label>
          <select id="ai-group" className="nb-input w-auto" value={groupBy} onChange={(e) => setGroupBy(e.target.value as GroupBy)}>
            <option value="day">Day</option>
            <option value="purpose">Purpose</option>
            <option value="model">Model</option>
          </select>
        </div>
        <div>
          <label htmlFor="ai-days" className="nb-label">
            Period
          </label>
          <select id="ai-days" className="nb-input w-auto" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[1, 7, 14, 30, 90].map((d) => (
              <option key={d} value={d}>
                Last {d} {d === 1 ? 'day' : 'days'}
              </option>
            ))}
          </select>
        </div>
      </div>
      {usage.isError && <Alert>{describeError(usage.error)}</Alert>}
      <div className="nb-card overflow-x-auto">
        <table className="nb-table min-w-full text-sm">
          <caption className="sr-only">AI usage by {groupBy}</caption>
          <thead>
            <tr>
              <th scope="col">{groupBy === 'day' ? 'Day' : groupBy === 'purpose' ? 'Purpose' : 'Model'}</th>
              <th scope="col">Calls</th>
              <th scope="col">Input tokens</th>
              <th scope="col">Output tokens</th>
              <th scope="col">Cache hit</th>
              <th scope="col">p95 latency</th>
              <th scope="col">Errors</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <td className="font-mono font-bold">{r.key}</td>
                <td className="font-mono">{r.calls.toLocaleString()}</td>
                <td className="font-mono">{r.inputTokens.toLocaleString()}</td>
                <td className="font-mono">{r.outputTokens.toLocaleString()}</td>
                <td className="font-mono">{pct(r.cacheHitRate)}</td>
                <td className="font-mono">{r.p95LatencyMs.toLocaleString()} ms</td>
                <td className={`font-mono ${r.errorRate > 0.05 ? 'font-bold' : ''}`}>{pct(r.errorRate)}</td>
              </tr>
            ))}
            {usage.data && rows.length === 0 && (
              <tr>
                <td colSpan={7} className="py-6 text-center font-medium">
                  No AI calls in this period.
                </td>
              </tr>
            )}
            {rows.length > 1 && (
              <tr className="font-bold">
                <th scope="row" className="text-left">
                  Total
                </th>
                <td className="font-mono">{totals.calls.toLocaleString()}</td>
                <td className="font-mono">{totals.input.toLocaleString()}</td>
                <td className="font-mono">{totals.output.toLocaleString()}</td>
                <td colSpan={3} />
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
