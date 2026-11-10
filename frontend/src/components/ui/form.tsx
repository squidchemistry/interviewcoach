import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  error?: string | undefined;
  hint?: string;
}

function describedBy(id: string, hint?: string, error?: string) {
  return [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ') || undefined;
}

function FieldMessages({ id, hint, error }: { id: string; hint?: string | undefined; error?: string | undefined }) {
  return (
    <>
      {hint && (
        <p id={`${id}-hint`} className="mt-1 text-xs font-medium text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1.5 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">
          {error}
        </p>
      )}
    </>
  );
}

/** Labelled input; errors and hints are linked with aria-describedby so screen readers announce them. */
export function TextField({ label, error, hint, className, ...input }: TextFieldProps) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="nb-label">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className="nb-input"
        {...input}
      />
      <FieldMessages id={id} hint={hint} error={error} />
    </div>
  );
}

interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string;
  error?: string | undefined;
  hint?: string;
}

export function TextAreaField({ label, error, hint, className, ...textarea }: TextAreaFieldProps) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="nb-label">
        {label}
      </label>
      <textarea
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, hint, error)}
        className="nb-input min-h-32 font-mono text-sm"
        {...textarea}
      />
      <FieldMessages id={id} hint={hint} error={error} />
    </div>
  );
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  const styles = { error: 'bg-nb-red', success: 'bg-nb-green', info: 'bg-nb-blue' }[tone];
  const icon = { error: '!', success: '✓', info: 'i' }[tone];
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`nb-accent flex items-start gap-3 px-4 py-3 text-sm font-semibold ${styles}`}
      style={{ boxShadow: '4px 4px 0 0 var(--shadow)' }}
    >
      <span aria-hidden="true" className="grid h-6 w-6 shrink-0 place-items-center border-2 border-[#0a0a0a] bg-white font-mono text-xs">
        {icon}
      </span>
      <div className="pt-0.5">{children}</div>
    </div>
  );
}
