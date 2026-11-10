/** Resolves an API path against VITE_API_BASE_URL (cross-origin deployments) or the current origin. */
export function apiUrl(path: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || window.location.origin;
  return new URL(path, base).toString();
}
