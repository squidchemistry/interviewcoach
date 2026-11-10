import { buttonClass } from './styles';

export function Pagination({
  page,
  pageSize,
  total,
  noun,
  onPage,
}: {
  page: number;
  pageSize: number;
  total: number | undefined;
  noun: string;
  onPage: (page: number) => void;
}) {
  const totalPages = total === undefined ? 1 : Math.max(1, Math.ceil(total / pageSize));
  return (
    <nav aria-label="Pagination" className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
      <span className="font-mono font-bold">
        {total === undefined ? 'Loading…' : `Page ${page} of ${totalPages} · ${total} ${noun}`}
      </span>
      <span className="flex gap-3">
        <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} disabled={page <= 1} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <button
          type="button"
          className={`${buttonClass.secondary} nb-btn-sm`}
          disabled={page >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Next
        </button>
      </span>
    </nav>
  );
}
