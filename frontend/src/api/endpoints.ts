import { apiRequest } from './http';
import { apiUrl } from './url';
import type {
  AdminUserPage,
  AdminUserResponse,
  AuthResponse,
  CompanyResponse,
  LoginRequest,
  ProfileResponse,
  ProvidersResponse,
  RegisterRequest,
  Role,
  UpdateProfileRequest,
  UserResponse,
} from './types';
import type {
  AdminQuestion,
  AiUsageRow,
  CheckoutResponse,
  CodeAttachment,
  CodeRunResponse,
  CommandResponse,
  CreateSessionRequest,
  Difficulty,
  InterviewMode,
  PackResponse,
  Page,
  ParsedResume,
  PlanResponse,
  ProgressResponse,
  PublicReport,
  QuestionResponse,
  QuestionStatus,
  QuestionSummary,
  QuestionType,
  ReindexResponse,
  Report,
  ResumeResponse,
  RevisionResponse,
  Rubric,
  SessionDetail,
  SessionStatus,
  SessionSummary,
  ShareLink,
  SimilarQuestion,
  SubmitAnswerRequest,
  SubscriptionResponse,
  TelemetryEvent,
  UpsertQuestionRequest,
  UsageResponse,
  VerifyResponse,
} from './models';

export const authApi = {
  register: (body: RegisterRequest) => apiRequest<AuthResponse>('/api/v1/auth/register', { method: 'POST', body }),
  login: (body: LoginRequest) => apiRequest<AuthResponse>('/api/v1/auth/login', { method: 'POST', body }),
  logout: () => apiRequest<void>('/api/v1/auth/logout', { method: 'POST' }),
  providers: () => apiRequest<ProvidersResponse>('/api/v1/auth/providers'),
};

export const meApi = {
  get: () => apiRequest<UserResponse>('/api/v1/me'),
  updateProfile: (body: UpdateProfileRequest) =>
    apiRequest<ProfileResponse>('/api/v1/me/profile', { method: 'PUT', body }),
  deleteAccount: () => apiRequest<void>('/api/v1/me', { method: 'DELETE', body: { confirm: 'DELETE' } }),
};

export const catalogueApi = {
  companies: () => apiRequest<CompanyResponse[]>('/api/v1/companies'),
};

export interface AdminUserQuery {
  q?: string;
  role?: Role;
  page: number;
  pageSize: number;
}

export const adminApi = {
  searchUsers: ({ q, role, page, pageSize }: AdminUserQuery) => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (q?.trim()) params.set('q', q.trim());
    if (role) params.set('role', role);
    return apiRequest<AdminUserPage>(`/api/v1/admin/users?${params.toString()}`);
  },
  changeRole: (id: string, role: Role) =>
    apiRequest<AdminUserResponse>(`/api/v1/admin/users/${encodeURIComponent(id)}/role`, {
      method: 'PATCH',
      body: { role },
    }),
};

/** Google sign-in starts with a full-page navigation (OAuth redirects), not a fetch. */
export function googleSignInUrl(redirect: string): string {
  return apiUrl(`/api/v1/auth/google?redirect=${encodeURIComponent(redirect)}`);
}

// ------------------------------------------------------------------ interview, progress, resumes, billing, catalogue

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

const enc = encodeURIComponent;

export interface SessionListQuery {
  status?: SessionStatus | '';
  mode?: InterviewMode | '';
  page: number;
  pageSize: number;
}

export const sessionsApi = {
  create: (body: CreateSessionRequest) => apiRequest<SessionSummary>('/api/v1/sessions', { method: 'POST', body }),
  list: (q: SessionListQuery) => apiRequest<Page<SessionSummary>>(`/api/v1/sessions${query({ ...q })}`),
  get: (id: string) => apiRequest<SessionDetail>(`/api/v1/sessions/${enc(id)}`),
  start: (id: string) => apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/start`, { method: 'POST' }),
  answer: (id: string, body: SubmitAnswerRequest) =>
    apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/answers`, { method: 'POST', body }),
  hint: (id: string, level: number) =>
    apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/hints`, { method: 'POST', body: { level } }),
  runCode: (id: string, body: CodeAttachment) =>
    apiRequest<CodeRunResponse>(`/api/v1/sessions/${enc(id)}/code/run`, { method: 'POST', body }),
  submitCode: (id: string, body: CodeAttachment) =>
    apiRequest<CodeRunResponse>(`/api/v1/sessions/${enc(id)}/code/submit`, { method: 'POST', body }),
  retryTurn: (id: string) => apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/retry-turn`, { method: 'POST' }),
  end: (id: string) => apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/end`, { method: 'POST' }),
  abandon: (id: string) => apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/abandon`, { method: 'POST' }),
  telemetry: (id: string, events: TelemetryEvent[]) =>
    apiRequest<void>(`/api/v1/sessions/${enc(id)}/telemetry`, { method: 'POST', body: { events } }),
  report: (id: string) => apiRequest<Report>(`/api/v1/sessions/${enc(id)}/report`),
  retryEvaluation: (id: string) =>
    apiRequest<CommandResponse>(`/api/v1/sessions/${enc(id)}/evaluation/retry`, { method: 'POST' }),
  share: (id: string, expiresInDays: number) =>
    apiRequest<ShareLink>(`/api/v1/sessions/${enc(id)}/share`, { method: 'POST', body: { expiresInDays } }),
  revokeShares: (id: string) => apiRequest<void>(`/api/v1/sessions/${enc(id)}/share`, { method: 'DELETE' }),
};

export const publicApi = {
  report: (token: string) => apiRequest<PublicReport>(`/api/v1/public/reports/${enc(token)}`, { anonymous: true }),
};

export const progressApi = {
  usage: () => apiRequest<UsageResponse>('/api/v1/me/usage'),
  progress: (mode?: InterviewMode | '') => apiRequest<ProgressResponse>(`/api/v1/me/progress${query({ mode })}`),
  revision: () => apiRequest<RevisionResponse>('/api/v1/me/revision-queue'),
};

export const resumesApi = {
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiRequest<ResumeResponse>('/api/v1/resumes', { method: 'POST', body: form });
  },
  list: () => apiRequest<ResumeResponse[]>('/api/v1/resumes'),
  get: (id: string) => apiRequest<ResumeResponse>(`/api/v1/resumes/${enc(id)}`),
  updateParsed: (id: string, body: ParsedResume) =>
    apiRequest<ResumeResponse>(`/api/v1/resumes/${enc(id)}/parsed`, { method: 'PATCH', body }),
  activate: (id: string) => apiRequest<ResumeResponse>(`/api/v1/resumes/${enc(id)}/activate`, { method: 'POST' }),
  remove: (id: string) => apiRequest<void>(`/api/v1/resumes/${enc(id)}`, { method: 'DELETE' }),
};

export const billingApi = {
  plans: () => apiRequest<PlanResponse[]>('/api/v1/billing/plans'),
  /** Resolves to `null` when the user has no subscription (204). */
  subscription: async () => (await apiRequest<SubscriptionResponse | undefined>('/api/v1/billing/subscription')) ?? null,
  subscribe: (planId: string) =>
    apiRequest<CheckoutResponse>('/api/v1/billing/subscriptions', { method: 'POST', body: { planId } }),
  verify: (body: { paymentId: string; subscriptionId: string; signature: string }) =>
    apiRequest<VerifyResponse>('/api/v1/billing/subscriptions/verify', { method: 'POST', body }),
  mockComplete: (subscriptionId: string) =>
    apiRequest<SubscriptionResponse>('/api/v1/billing/mock/complete', { method: 'POST', body: { subscriptionId } }),
  cancel: () => apiRequest<SubscriptionResponse>('/api/v1/billing/subscriptions/cancel', { method: 'POST' }),
};

export interface QuestionQuery {
  type?: QuestionType | '';
  difficulty?: Difficulty | '';
  tag?: string;
  company?: string;
  q?: string;
  page: number;
  pageSize: number;
}

export const questionsApi = {
  search: (q: QuestionQuery) =>
    apiRequest<Page<QuestionSummary>>(`/api/v1/questions${query({ ...q, q: q.q?.trim() })}`),
  get: (idOrSlug: string) => apiRequest<QuestionResponse>(`/api/v1/questions/${enc(idOrSlug)}`),
  similar: (idOrSlug: string, k = 5) =>
    apiRequest<SimilarQuestion[]>(`/api/v1/questions/${enc(idOrSlug)}/similar${query({ k })}`),
  tags: () => apiRequest<string[]>('/api/v1/tags'),
  packs: () => apiRequest<PackResponse[]>('/api/v1/packs'),
};

export interface AdminQuestionQuery {
  type?: QuestionType | '';
  status?: QuestionStatus | '';
  difficulty?: Difficulty | '';
  q?: string;
  page: number;
  pageSize: number;
}

export const adminContentApi = {
  searchQuestions: (q: AdminQuestionQuery) =>
    apiRequest<Page<AdminQuestion>>(`/api/v1/admin/questions${query({ ...q, q: q.q?.trim() })}`),
  getQuestion: (id: string) => apiRequest<AdminQuestion>(`/api/v1/admin/questions/${enc(id)}`),
  createQuestion: (body: UpsertQuestionRequest) =>
    apiRequest<AdminQuestion>('/api/v1/admin/questions', { method: 'POST', body }),
  updateQuestion: (id: string, body: UpsertQuestionRequest) =>
    apiRequest<AdminQuestion>(`/api/v1/admin/questions/${enc(id)}`, { method: 'PUT', body }),
  changeStatus: (id: string, status: QuestionStatus) =>
    apiRequest<AdminQuestion>(`/api/v1/admin/questions/${enc(id)}/status`, { method: 'POST', body: { status } }),
  reindex: () => apiRequest<ReindexResponse>('/api/v1/admin/embeddings/reindex', { method: 'POST' }),
  rubrics: () => apiRequest<Rubric[]>('/api/v1/admin/rubrics'),
  createRubric: (body: { key: string; definition: unknown; changelog?: string }) =>
    apiRequest<Rubric>('/api/v1/admin/rubrics', { method: 'POST', body }),
  activateRubric: (id: string) => apiRequest<Rubric>(`/api/v1/admin/rubrics/${enc(id)}/activate`, { method: 'POST' }),
  aiUsage: (groupBy: 'day' | 'purpose' | 'model', days: number) =>
    apiRequest<AiUsageRow[]>(`/api/v1/admin/ai-usage${query({ groupBy, days })}`),
};
