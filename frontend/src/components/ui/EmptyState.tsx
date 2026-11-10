import type { ReactNode } from 'react';

export function EmptyState({ title, children, action, color = 'bg-nb-blue' }: { title: string; children?: ReactNode; action?: ReactNode; color?: string }) {
  return (
    <section className={`nb-accent ${color} p-8 text-center`}>
      <h2 className="nb-heading text-2xl">{title}</h2>
      {children && <div className="mx-auto mt-2 max-w-xl font-medium">{children}</div>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </section>
  );
}
