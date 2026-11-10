import { Link } from 'react-router-dom';
import { buttonClass } from './styles';

/** Big-number status pages (404, 403, …). */
export function StatusPage({ code, title, message, color = 'bg-nb-pink' }: { code: string; title: string; message: string; color?: string }) {
  return (
    <div className="mx-auto max-w-xl px-4 py-16 text-center sm:px-6">
      <p className={`nb-accent mx-auto inline-block rotate-[-3deg] px-5 py-2 font-mono text-5xl font-bold ${color}`}>{code}</p>
      <h1 className="nb-heading mt-8 text-3xl">{title}</h1>
      <p className="mt-3 font-medium text-muted">{message}</p>
      <Link to="/" className={`${buttonClass.primary} mt-8`}>
        Back to home
      </Link>
    </div>
  );
}
