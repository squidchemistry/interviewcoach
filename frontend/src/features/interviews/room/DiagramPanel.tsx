import type { Mermaid } from 'mermaid';
import { useEffect, useId, useState } from 'react';

export const MAX_DIAGRAM_LENGTH = 10_240;
const DEBOUNCE_MS = 450;

let mermaidReady: Promise<Mermaid> | null = null;

function loadMermaid(dark: boolean) {
  mermaidReady ??= import('mermaid').then(({ default: mermaid }) => {
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: dark ? 'dark' : 'neutral' });
    return mermaid;
  });
  return mermaidReady;
}

/** Mermaid source editor with a debounced live preview (securityLevel 'strict': no scripts, no HTML labels). */
export function DiagramPanel({ value, onChange, onPaste, disabled }: { value: string; onChange: (v: string) => void; onPaste: (size: number) => void; disabled: boolean }) {
  const baseId = useId().replace(/[^a-zA-Z0-9]/g, '');
  const [svg, setSvg] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [rendering, setRendering] = useState(false);

  useEffect(() => {
    if (!value.trim()) {
      setSvg('');
      setError(null);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      setRendering(true);
      const dark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
      const renderId = `mmd-${baseId}-${Date.now()}`;
      try {
        const mermaid = await loadMermaid(dark);
        await mermaid.parse(value);
        const result = await mermaid.render(renderId, value);
        if (!cancelled) {
          setSvg(result.svg);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message.split('\n').slice(0, 4).join('\n') : 'Invalid diagram');
        // mermaid leaves its temporary render container behind on errors
        document.getElementById(`d${renderId}`)?.remove();
      } finally {
        if (!cancelled) setRendering(false);
      }
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [value, baseId]);

  return (
    <section aria-labelledby="diagram-heading" className="nb-card">
      <div className="border-b-3 border-ink p-3">
        <h2 id="diagram-heading" className="nb-heading text-lg">
          Class diagram
        </h2>
        <p className="text-xs font-medium text-muted">
          Sketch your design in Mermaid (e.g. <code className="font-mono">classDiagram</code>). It is attached to every answer you send.
        </p>
      </div>
      <div className="grid gap-0 lg:grid-cols-2">
        <div className="p-3">
          <label htmlFor="diagram-source" className="nb-label">
            Mermaid source
          </label>
          <textarea
            id="diagram-source"
            className="nb-input min-h-64 font-mono text-xs"
            value={value}
            maxLength={MAX_DIAGRAM_LENGTH}
            disabled={disabled}
            onChange={(e) => onChange(e.target.value)}
            onPaste={(e) => onPaste(e.clipboardData.getData('text').length)}
            spellCheck={false}
            placeholder={'classDiagram\n  class ParkingLot {\n    +park(vehicle) Ticket\n  }'}
          />
        </div>
        <div className="border-t-3 border-ink p-3 lg:border-t-0 lg:border-l-3">
          <p className="nb-label" id="diagram-preview-label">
            Preview {rendering && <span className="font-mono text-xs">(rendering…)</span>}
          </p>
          <div aria-live="polite" aria-labelledby="diagram-preview-label">
            {error ? (
              <pre role="alert" className="whitespace-pre-wrap bg-nb-red p-2 font-mono text-xs text-[#0a0a0a]">
                {error}
              </pre>
            ) : svg ? (
              <div className="overflow-auto bg-white p-2 [&_svg]:max-w-full" dangerouslySetInnerHTML={{ __html: svg }} />
            ) : (
              <p className="text-sm font-medium text-muted">Your diagram preview appears here.</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
