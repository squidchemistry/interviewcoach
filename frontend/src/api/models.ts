/**
 * Hand-written types for the interview, progress, resume, billing, catalogue and admin-content APIs.
 * Sources of truth are the backend Java records (InterviewDtos, ProgressDtos, BillingDtos, ResumeResponse,
 * ParsedResume, QuestionDtos, QuestionDetails, AiUsageRow). Jackson omits nulls, so nullable fields are optional.
 */
import type { CodeLanguage, TargetRole } from './types';

export type InterviewMode = 'DSA' | 'LLD' | 'BEHAVIOURAL' | 'RESUME_DEEP_DIVE';
export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'EVALUATING' | 'COMPLETED' | 'ABANDONED';
export type SessionStage = 'QUESTION' | 'ANSWER' | 'FOLLOW_UP';
export type EndReason = 'COMPLETED_ALL' | 'ENDED_BY_USER' | 'INACTIVITY' | 'ABANDONED_BY_USER' | 'BUDGET_EXHAUSTED';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type TurnRole = 'INTERVIEWER' | 'CANDIDATE' | 'SYSTEM';
export type TurnKind = 'QUESTION' | 'ANSWER' | 'FOLLOW_UP' | 'HINT' | 'CODE_RUN' | 'SYSTEM_NOTICE';
export type ContentFormat = 'TEXT' | 'MARKDOWN' | 'CODE' | 'MERMAID';
export type InputMode = 'TEXT' | 'VOICE';
export type EvaluationStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type QuestionType = 'DSA' | 'LLD' | 'BEHAVIOURAL';
export type QuestionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

// ------------------------------------------------------------------ sessions

export interface CreateSessionRequest {
  mode: InterviewMode;
  questionCount?: number;
  startDifficulty?: Difficulty;
  targetRole?: TargetRole;
  company?: string;
  packSlug?: string;
  resumeId?: string;
  voice?: boolean;
}

export interface CodeAttachment {
  language: CodeLanguage;
  source: string;
}

export interface SubmitAnswerRequest {
  clientMessageId: string;
  content: string;
  inputMode?: InputMode;
  code?: CodeAttachment;
  diagram?: { mermaid: string };
}

export interface SessionSummary {
  id: string;
  mode: InterviewMode;
  status: SessionStatus;
  stage?: SessionStage;
  processing: boolean;
  targetRole?: TargetRole;
  company?: string;
  packSlug?: string;
  plannedQuestions: number;
  currentIndex: number;
  currentDifficulty?: Difficulty;
  voiceEnabled: boolean;
  endReason?: EndReason;
  overallScore?: number;
  createdAt: string;
  startedAt?: string;
  endedAt?: string;
}

export interface Turn {
  id: string;
  seq: number;
  role: TurnRole;
  kind: TurnKind;
  content: string;
  format?: ContentFormat;
  inputMode?: InputMode;
  sessionQuestionId?: string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface Param {
  name: string;
  type: string;
}

export interface SampleTest {
  input: unknown;
  expected: unknown;
}

export interface DsaView {
  functionName: string;
  params: Param[];
  returnType: string;
  samples: SampleTest[];
  hiddenTestCount: number;
  starterCode: Partial<Record<CodeLanguage, string>>;
}

export interface LldView {
  requirements: string[];
  focusAreas: string[];
}

export interface BehaviouralView {
  competency: string;
  leadershipPrinciple?: string;
}

/**
 * {@code SessionQuestionResponse.context}: for bank questions this is the candidate-visible QuestionResponse
 * ({dsa|lld|behavioural}); for resume deep-dive it is {angle, projectName?, expectedSignals}.
 */
export interface QuestionContext {
  dsa?: DsaView;
  lld?: LldView;
  behavioural?: BehaviouralView;
  angle?: string;
  projectName?: string;
}

export interface SessionQuestion {
  id: string;
  position: number;
  title: string;
  promptMd: string;
  difficulty: Difficulty;
  difficultyReason?: string;
  tags: string[];
  context?: QuestionContext;
  hintsUsed: number;
  followUps: number;
  turnScore?: number;
  completed: boolean;
}

export interface SessionDetail {
  session: SessionSummary;
  questions: SessionQuestion[];
  transcript: Turn[];
  lastEventId?: string;
  evaluationStatus?: EvaluationStatus;
  codeExecutionEnabled: boolean;
}

export interface CommandResponse {
  status: SessionStatus;
  stage?: SessionStage;
  processing: boolean;
  lastEventId?: string;
}

export interface TestResult {
  index: number;
  sample: boolean;
  verdict: string;
  input?: unknown;
  expected?: unknown;
  actual?: string;
  stdout?: string;
  stderr?: string;
  timeMs?: number;
}

export interface CodeRunResponse {
  submissionId: string;
  kind: string;
  verdict: string;
  passed: number;
  total: number;
  runtimeMs?: number;
  executor?: string;
  executed: boolean;
  compileOutput?: string;
  tests: TestResult[];
}

export type TelemetryType = 'PASTE' | 'TAB_HIDDEN' | 'TAB_VISIBLE';

export interface TelemetryEvent {
  type: TelemetryType;
  at: string;
  size?: number;
}

// ------------------------------------------------------------------ reports

export interface Evidence {
  quote: string;
  turnSeq: number;
}

export interface Competency {
  competency: string;
  label: string;
  score: number;
  weight: number;
  lowConfidence: boolean;
  rationale?: string;
  evidence?: Evidence[];
  adjustments?: string[];
}

export interface NextStep {
  action: string;
  why: string;
  resourceType: 'PRACTICE' | 'READ' | 'MOCK' | 'REFLECT';
  relatedTag?: string;
}

export interface StarPart {
  score: number;
  justification: string;
  quote?: string;
}

export interface StarBreakdown {
  situation: StarPart;
  task: StarPart;
  action: StarPart;
  result: StarPart;
}

export interface QuestionReport {
  position: number;
  sessionQuestionId?: string;
  questionId?: string;
  title: string;
  difficulty: Difficulty;
  difficultyReason?: string;
  tags?: string[];
  hintsUsed: number;
  followUps: number;
  turnScore?: number;
  code?: { verdict: string; passed: number; total: number; executed: boolean; language: string };
  modelAnswerMd?: string;
  keyMissedPoints?: string[];
  star?: StarBreakdown;
  answered?: boolean;
}

export interface IntegrityNote {
  type: string;
  detail?: string;
}

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Evaluation {
  status: EvaluationStatus;
  attempts: number;
  error?: string;
  overallScore?: number;
  confidence?: Confidence;
  summary?: string;
  competencies?: Competency[];
  strengths?: string[];
  gaps?: string[];
  nextSteps?: NextStep[];
  perQuestion?: QuestionReport[];
  integrity?: IntegrityNote[];
  rubric?: { key: string; version: number };
  evaluator?: { provider: string; model: string; promptVersion: string };
  completedAt?: string;
}

export interface ShareLink {
  id: string;
  url?: string;
  expiresAt: string;
  revokedAt?: string;
  viewCount: number;
  createdAt: string;
}

export interface Report {
  session: SessionSummary;
  evaluation: Evaluation;
  transcript: Turn[];
  shareLinks?: ShareLink[];
}

export interface PublicReport {
  candidateFirstName: string;
  mode: InterviewMode;
  targetRole?: TargetRole;
  company?: string;
  completedAt?: string;
  overallScore?: number;
  confidence?: Confidence;
  summary?: string;
  competencies: Array<{ competency: string; label: string; score: number; lowConfidence: boolean }>;
  strengths?: string[];
  gaps?: string[];
  questions: Array<{ position: number; title: string; difficulty: string; turnScore?: number }>;
}

// ------------------------------------------------------------------ progress

export interface UsageResponse {
  period: string;
  plan: string;
  interviewsStarted: number;
  interviewLimit?: number;
  tokensUsedToday: number;
}

export interface Totals {
  interviews: number;
  completed: number;
  averageScore?: number;
  bestScore?: number;
  questionsAnswered: number;
}

export interface ModeSummary {
  mode: InterviewMode;
  completed: number;
  averageScore?: number;
}

export interface TrendPoint {
  sessionId: string;
  mode: InterviewMode;
  competency: string;
  score: number;
  at: string;
}

export interface SessionScore {
  sessionId: string;
  mode: InterviewMode;
  overallScore: number;
  at: string;
}

export interface Topic {
  tag: string;
  attempts: number;
  averageScore: number;
  lastScore: number;
  dueAt: string;
  intervalDays: number;
}

export interface ProgressResponse {
  totals: Totals;
  modes: ModeSummary[];
  scores: SessionScore[];
  trends: TrendPoint[];
  weakTopics: Topic[];
  recent: SessionSummary[];
}

export interface SuggestedQuestion {
  id: string;
  slug: string;
  title: string;
  type: QuestionType;
  difficulty: Difficulty;
}

export interface RevisionItem {
  tag: string;
  dueAt: string;
  due: boolean;
  averageScore: number;
  attempts: number;
  intervalDays: number;
  suggestedQuestions: SuggestedQuestion[];
}

export interface RevisionResponse {
  items: RevisionItem[];
}

// ------------------------------------------------------------------ resumes

export type SkillCategory = 'LANGUAGE' | 'FRAMEWORK' | 'DATABASE' | 'CLOUD' | 'TOOL' | 'CONCEPT';

export interface ParsedResume {
  summary?: string;
  skills: Array<{ name: string; category: SkillCategory | string }>;
  projects: Array<{ name: string; summary: string; techStack: string[]; highlights: string[] }>;
  experience: Array<{ company: string; title: string; start?: string; end?: string; highlights: string[] }>;
  integrityFlags?: string[];
}

export type ResumeStatus = 'PARSING' | 'PARSED' | 'FAILED';

export interface ResumeResponse {
  id: string;
  filename: string;
  sizeBytes: number;
  pageCount: number;
  status: ResumeStatus;
  active: boolean;
  parsed?: ParsedResume;
  /** Free-form integrity notes: strings or {type, detail} objects. */
  integrity?: unknown;
  error?: string;
  createdAt: string;
  updatedAt?: string;
}

// ------------------------------------------------------------------ billing

export interface PlanResponse {
  id: string;
  name: string;
  priceInPaise: number;
  currency: string;
  interval: string;
  features: string[];
}

export interface SubscriptionResponse {
  id: string;
  provider: string;
  planId: string;
  status: string;
  currentEnd?: string;
  createdAt: string;
}

export interface CheckoutResponse {
  provider: 'RAZORPAY' | 'MOCK' | string;
  subscriptionId: string;
  keyId?: string;
  name?: string;
  email?: string;
  priceInPaise: number;
}

export interface VerifyResponse {
  status: 'PENDING_WEBHOOK' | 'ACTIVE' | string;
}

// ------------------------------------------------------------------ catalogue

export interface QuestionSummary {
  id: string;
  slug: string;
  type: QuestionType;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  companies: string[];
}

export interface QuestionResponse extends QuestionSummary {
  bodyMd: string;
  dsa?: DsaView;
  lld?: LldView;
  behavioural?: BehaviouralView;
}

export interface SimilarQuestion {
  question: QuestionSummary;
  similarity: number;
}

export interface PackResponse {
  slug: string;
  name: string;
  description: string;
  mode: QuestionType;
  company?: string;
  pro: boolean;
  questionCount: number;
}

// ------------------------------------------------------------------ admin content

export interface AdminQuestion {
  id: string;
  slug: string;
  type: QuestionType;
  title: string;
  bodyMd: string;
  difficulty: Difficulty;
  status: QuestionStatus;
  tags: string[];
  companies: string[];
  detail: unknown;
  source?: string;
  version: number;
  updatedAt: string;
}

export interface UpsertQuestionRequest {
  type: QuestionType;
  slug: string;
  title: string;
  bodyMd: string;
  difficulty: Difficulty;
  status: QuestionStatus;
  tags: string[];
  companies: string[];
  detail: unknown;
  expectedVersion?: number;
}

export interface Rubric {
  id: string;
  key: string;
  version: number;
  active: boolean;
  definition: unknown;
  changelog?: string;
  createdAt: string;
}

export interface AiUsageRow {
  key: string;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  cacheHitRate: number;
  p95LatencyMs: number;
  errorRate: number;
}

export interface ReindexResponse {
  embedded: number;
  model: string;
}
