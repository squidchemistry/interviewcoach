export function PageSpinner({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" className="flex items-center justify-center gap-3 px-4 py-24 font-bold">
      <span aria-hidden="true" className="flex gap-1">
        {['bg-nb-yellow', 'bg-nb-pink', 'bg-nb-blue'].map((c, i) => (
          <span
            key={c}
            className={`h-4 w-4 animate-bounce border-2 border-ink ${c}`}
            style={{ animationDelay: `${i * 120}ms` }}
          />
        ))}
      </span>
      <span>{label}…</span>
    </div>
  );
}
