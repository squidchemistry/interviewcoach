import type { ReactNode } from 'react';

/** Page title block: big heading, optional kicker badge, description and actions. */
export function PageHeader({
  title,
  kicker,
  kickerColor = 'bg-nb-yellow',
  description,
  actions,
}: {
  title: string;
  kicker?: string;
  kickerColor?: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="max-w-3xl">
        {kicker && <p className={`nb-badge mb-3 ${kickerColor}`}>{kicker}</p>}
        <h1 className="nb-heading text-3xl sm:text-4xl">{title}</h1>
        {description && <div className="mt-2 font-medium text-muted">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
    </header>
  );
}
