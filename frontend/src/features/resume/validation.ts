export const MAX_RESUME_BYTES = 5 * 1024 * 1024;

/** Client-side pre-check (the server re-validates type, size and page count). Returns an error message or null. */
export function validateResumeFile(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || (file.type === '' && file.name.toLowerCase().endsWith('.pdf'));
  if (!isPdf) return 'Only PDF files are supported.';
  if (file.size === 0) return 'That file is empty.';
  if (file.size > MAX_RESUME_BYTES) return 'The PDF must be 5 MB or smaller.';
  return null;
}

/** Integrity notes come as strings or {type, detail} objects; normalise to display strings. */
export function integrityNotes(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === 'string') return item;
      if (item && typeof item === 'object') {
        const { type, detail } = item as { type?: unknown; detail?: unknown };
        return [type, detail].filter((x) => typeof x === 'string' && x).join(': ');
      }
      return '';
    })
    .filter(Boolean);
}
