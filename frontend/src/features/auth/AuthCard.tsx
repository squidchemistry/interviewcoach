import type { ReactNode } from 'react';

export function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md px-4 py-10 sm:py-16">
      <div className="nb-card p-6 sm:p-8">
        <h1 className="nb-heading text-3xl">{title}</h1>
        <p className="mt-2 mb-6 font-medium text-muted">{subtitle}</p>
        {children}
      </div>
    </div>
  );
}
