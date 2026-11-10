import { useId, useState } from 'react';

export interface ChartPoint {
  at: string;
  value: number;
  label?: string;
}

const W = 640;
const H = 240;
const PAD = { top: 16, right: 16, bottom: 32, left: 40 };

/**
 * Single-series line chart in plain SVG: 2px ink line, 8px markers, recessive grid, one y-axis, hover/focus
 * tooltip, and a visually hidden data table so the values are available without the picture.
 */
export function LineChart({ title, description, points, max, unit }: { title: string; description: string; points: ChartPoint[]; max: number; unit: string }) {
  const id = useId();
  const [active, setActive] = useState<number | null>(null);
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;
  const x = (i: number) => PAD.left + (points.length <= 1 ? innerW / 2 : (i / (points.length - 1)) * innerW);
  const y = (v: number) => PAD.top + innerH - (Math.max(0, Math.min(max, v)) / max) * innerH;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((t) => Math.round(t * max));
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');
  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  const activePoint = active !== null ? points[active] : undefined;

  return (
    <figure className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-labelledby={`${id}-t ${id}-d`}>
        <title id={`${id}-t`}>{title}</title>
        <desc id={`${id}-d`}>{description}</desc>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="currentColor" strokeOpacity={t === 0 ? 0.8 : 0.15} strokeWidth={t === 0 ? 2 : 1} />
            <text x={PAD.left - 8} y={y(t) + 4} textAnchor="end" fontSize="11" fill="currentColor" fontFamily="var(--font-mono)">
              {t}
            </text>
          </g>
        ))}
        {points.length > 0 && (
          <>
            <text x={x(0)} y={H - 10} textAnchor={points.length > 1 ? 'start' : 'middle'} fontSize="11" fill="currentColor" fontFamily="var(--font-mono)">
              {fmtDate(points[0]!.at)}
            </text>
            {points.length > 1 && (
              <text x={x(points.length - 1)} y={H - 10} textAnchor="end" fontSize="11" fill="currentColor" fontFamily="var(--font-mono)">
                {fmtDate(points[points.length - 1]!.at)}
              </text>
            )}
          </>
        )}
        {activePoint && <line x1={x(active!)} x2={x(active!)} y1={PAD.top} y2={PAD.top + innerH} stroke="currentColor" strokeOpacity={0.35} strokeDasharray="4 4" />}
        <path d={path} fill="none" stroke="currentColor" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
        {points.map((p, i) => (
          <g key={`${p.at}-${i}`} onMouseEnter={() => setActive(i)} onMouseLeave={() => setActive(null)}>
            <circle cx={x(i)} cy={y(p.value)} r={14} fill="transparent" />
            <circle cx={x(i)} cy={y(p.value)} r={active === i ? 6 : 4.5} fill="var(--yellow)" stroke="var(--surface)" strokeWidth={2} />
            <circle cx={x(i)} cy={y(p.value)} r={active === i ? 6 : 4.5} fill="none" stroke="currentColor" strokeWidth={1.5} />
          </g>
        ))}
      </svg>
      {activePoint && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute border-2 border-ink bg-surface px-2 py-1 font-mono text-xs font-bold shadow-[3px_3px_0_0_var(--shadow)]"
          style={{ left: `${(x(active!) / W) * 100}%`, top: `${(y(activePoint.value) / H) * 100}%`, transform: 'translate(-50%, -130%)' }}
        >
          {activePoint.value.toFixed(1)}
          {unit} · {fmtDate(activePoint.at)}
          {activePoint.label ? ` · ${activePoint.label}` : ''}
        </div>
      )}
      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Value</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p, i) => (
            <tr key={`${p.at}-${i}`}>
              <td>{new Date(p.at).toLocaleDateString()}</td>
              <td>
                {p.value.toFixed(1)}
                {unit}
                {p.label ? ` (${p.label})` : ''}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
