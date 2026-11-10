/**
 * Fetch wrapper for the AI Interview Coach API.
 * - Sends the in-memory access token as a bearer token (never on /auth/* endpoints, which use the cookie).
 * - On 401, refreshes the session once (single-flight) and retries the request.
 * - Turns RFC 7807 problem+json errors into typed {@link ApiError}s that carry `code` and `requestId`.
 */
import { getSession, refreshSession } from './session';
import { apiUrl } from './url';

export { apiUrl } from './url';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  requestId?: string;
  errors?: Array<{ path: string; message: string }>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId: string | undefined;
  readonly problem: ProblemDetail | undefined;
  readonly retryAfterSeconds: number | undefined;

  constructor(status: number, problem?: ProblemDetail, requestId?: string, retryAfterSeconds?: number) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN');
    this.requestId = problem?.requestId ?? requestId;
    this.problem = problem;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /** Field errors keyed by field name: `body.email` → `email`. */
  fieldErrors(): Record<string, string> {
    const result: Record<string, string> = {};
    for (const error of this.problem?.errors ?? []) {
      const field = error.path.replace(/^body\./, '');
      result[field] ??= error.message;
    }
    return result;
  }
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Extra non-2xx statuses whose JSON body is a valid response (e.g. 503 from the health endpoint). */
  acceptStatuses?: number[];
  /** Send no bearer token and never refresh (public endpoints such as shared reports). */
  anonymous?: boolean;
}

const AUTH_PATH = '/api/v1/auth/';

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, acceptStatuses = [], anonymous = false, headers, ...init } = options;
  const isAuthEndpoint = path.startsWith(AUTH_PATH) || anonymous;
  // FormData (multipart uploads) goes as-is so the browser sets the Content-Type with its boundary.
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;

  const send = (token: string | null) =>
    fetch(apiUrl(path), {
      ...init,
      credentials: 'include',
      headers: {
        Accept: 'application/json, application/problem+json',
        ...(body !== undefined && !isForm ? { 'Content-Type': 'application/json' } : {}),
        ...(token && !isAuthEndpoint ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: isForm ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
    });

  let response: Response;
  try {
    const token = getSession().accessToken;
    response = await send(token);
    if (response.status === 401 && !isAuthEndpoint && token !== null) {
      const refreshed = await refreshSession();
      if (refreshed) {
        response = await send(refreshed.accessToken);
      }
    }
  } catch {
    throw new ApiError(0, { status: 0, title: 'Network error', detail: 'Could not reach the server' });
  }

  if (response.ok || acceptStatuses.includes(response.status)) {
    if (response.status === 204 || response.status === 205) {
      return undefined as T;
    }
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  const requestId = response.headers.get('X-Request-Id') ?? undefined;
  const retryAfter = Number(response.headers.get('Retry-After')) || undefined;
  let problem: ProblemDetail | undefined;
  if (response.headers.get('Content-Type')?.includes('json')) {
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      problem = undefined;
    }
  }
  throw new ApiError(response.status, problem, requestId, retryAfter);
}
