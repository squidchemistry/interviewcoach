/**
 * Interview-room state: the server snapshot (GET /sessions/{id}) plus everything the SSE stream adds to it.
 * Pure reducer so stream handling is unit-testable and replay-safe (turns dedupe by id, tokens only feed the
 * in-flight streaming bubble that `turn.completed` replaces).
 */
import type {
  CommandResponse,
  Difficulty,
  EndReason,
  EvaluationStatus,
  QuestionContext,
  SessionDetail,
  SessionQuestion,
  SessionStage,
  SessionStatus,
  Turn,
  TurnKind,
  TurnRole,
} from '../../../api/models';

export interface StreamingTurn {
  turnId: string;
  kind: TurnKind | string;
  role: TurnRole | string;
  text: string;
}

export interface Assessment {
  sessionQuestionId: string;
  position: number;
  turnScore?: number;
  nextDifficulty?: Difficulty;
  reason?: string;
}

export interface StreamError {
  code: string;
  message: string;
  retryable: boolean;
}

export interface EvaluationProgress {
  status: EvaluationStatus;
  step?: string;
}

export interface RoomState {
  detail: SessionDetail | null;
  streaming: StreamingTurn[];
  assessment: Assessment | null;
  evaluation: EvaluationProgress | null;
  error: StreamError | null;
  /** Candidate answer sent but not yet echoed back by `turn.completed`. */
  pendingAnswer: string | null;
}

export const initialRoomState: RoomState = {
  detail: null,
  streaming: [],
  assessment: null,
  evaluation: null,
  error: null,
  pendingAnswer: null,
};

export interface StateChangedEvent {
  status: SessionStatus;
  stage?: SessionStage;
  processing: boolean;
  currentIndex: number;
  currentDifficulty?: Difficulty;
  endReason?: EndReason;
}

export interface QuestionChangedEvent {
  sessionQuestionId: string;
  position: number;
  title: string;
  promptMd: string;
  difficulty: Difficulty;
  difficultyReason?: string;
  tags?: string[];
  context?: QuestionContext;
}

export interface TurnCompletedEvent {
  turnId: string;
  seq: number;
  role: TurnRole;
  kind: TurnKind;
  content: string;
  format?: Turn['format'];
  sessionQuestionId?: string;
  metadata?: Record<string, unknown>;
}

export type RoomAction =
  | { type: 'loaded'; detail: SessionDetail }
  | { type: 'command'; response: CommandResponse }
  | { type: 'answer.pending'; content: string }
  | { type: 'answer.failed' }
  | { type: 'error.dismiss' }
  | { type: 'event'; event: string; data: unknown };

function upsertTurn(transcript: Turn[], turn: Turn): Turn[] {
  const without = transcript.filter((t) => t.id !== turn.id);
  return [...without, turn].sort((a, b) => a.seq - b.seq);
}

function upsertQuestion(questions: SessionQuestion[], q: QuestionChangedEvent): SessionQuestion[] {
  const existing = questions.find((x) => x.id === q.sessionQuestionId);
  const next: SessionQuestion = {
    hintsUsed: 0,
    followUps: 0,
    completed: false,
    ...existing,
    id: q.sessionQuestionId,
    position: q.position,
    title: q.title,
    promptMd: q.promptMd,
    difficulty: q.difficulty,
    difficultyReason: q.difficultyReason,
    tags: q.tags ?? [],
    context: q.context,
  };
  return [...questions.filter((x) => x.id !== next.id), next].sort((a, b) => a.position - b.position);
}

function patchSession(state: RoomState, patch: Partial<SessionDetail['session']>): RoomState {
  if (!state.detail) return state;
  return { ...state, detail: { ...state.detail, session: { ...state.detail.session, ...patch } } };
}

export function roomReducer(state: RoomState, action: RoomAction): RoomState {
  switch (action.type) {
    case 'loaded':
      // A fresh snapshot already contains every completed turn; drop bubbles that it covers.
      return {
        ...state,
        detail: action.detail,
        streaming: state.streaming.filter((s) => !action.detail.transcript.some((t) => t.id === s.turnId)),
      };
    case 'command':
      return patchSession(state, {
        status: action.response.status,
        stage: action.response.stage,
        processing: action.response.processing,
      });
    case 'answer.pending':
      return { ...state, pendingAnswer: action.content, error: null };
    case 'answer.failed':
      return { ...state, pendingAnswer: null };
    case 'error.dismiss':
      return { ...state, error: null };
    case 'event':
      return applyEvent(state, action.event, action.data);
    default:
      return state;
  }
}

function applyEvent(state: RoomState, event: string, raw: unknown): RoomState {
  const data = (raw ?? {}) as Record<string, unknown>;
  switch (event) {
    case 'turn.started': {
      const turnId = String(data.turnId);
      if (state.streaming.some((s) => s.turnId === turnId) || state.detail?.transcript.some((t) => t.id === turnId)) {
        return state;
      }
      return {
        ...state,
        error: null,
        streaming: [...state.streaming, { turnId, kind: String(data.kind ?? ''), role: String(data.role ?? 'INTERVIEWER'), text: '' }],
      };
    }
    case 'token': {
      const turnId = String(data.turnId);
      const delta = typeof data.delta === 'string' ? data.delta : '';
      if (state.detail?.transcript.some((t) => t.id === turnId)) return state; // replayed token of a finished turn
      const existing = state.streaming.find((s) => s.turnId === turnId);
      const streaming = existing
        ? state.streaming.map((s) => (s.turnId === turnId ? { ...s, text: s.text + delta } : s))
        : [...state.streaming, { turnId, kind: '', role: 'INTERVIEWER', text: delta }];
      return { ...state, streaming };
    }
    case 'turn.completed': {
      const e = data as unknown as TurnCompletedEvent;
      if (!state.detail) return state;
      const turn: Turn = {
        id: e.turnId,
        seq: e.seq,
        role: e.role,
        kind: e.kind,
        content: e.content,
        format: e.format,
        sessionQuestionId: e.sessionQuestionId,
        metadata: e.metadata,
      };
      let questions = state.detail.questions;
      if (e.kind === 'HINT') {
        const level = Number((e.metadata as { hintLevel?: unknown } | undefined)?.hintLevel ?? 0);
        questions = questions.map((q) =>
          q.id === e.sessionQuestionId ? { ...q, hintsUsed: Math.max(q.hintsUsed, level || q.hintsUsed + 1) } : q,
        );
      }
      return {
        ...state,
        pendingAnswer: e.role === 'CANDIDATE' ? null : state.pendingAnswer,
        streaming: state.streaming.filter((s) => s.turnId !== e.turnId),
        detail: { ...state.detail, questions, transcript: upsertTurn(state.detail.transcript, turn) },
      };
    }
    case 'state.changed': {
      const e = data as unknown as StateChangedEvent;
      return patchSession(state, {
        status: e.status,
        stage: e.stage,
        processing: e.processing,
        currentIndex: e.currentIndex,
        currentDifficulty: e.currentDifficulty,
        endReason: e.endReason,
      });
    }
    case 'question.changed': {
      if (!state.detail) return state;
      const e = data as unknown as QuestionChangedEvent;
      return { ...state, detail: { ...state.detail, questions: upsertQuestion(state.detail.questions, e) } };
    }
    case 'assessment': {
      const e = data as unknown as Assessment;
      if (!state.detail) return { ...state, assessment: e };
      const questions = state.detail.questions.map((q) =>
        q.id === e.sessionQuestionId ? { ...q, turnScore: e.turnScore, completed: true } : q,
      );
      return { ...state, assessment: e, detail: { ...state.detail, questions } };
    }
    case 'evaluation.progress':
      return { ...state, evaluation: data as unknown as EvaluationProgress };
    case 'error': {
      const e = data as Partial<StreamError>;
      return {
        ...state,
        streaming: [],
        pendingAnswer: null,
        error: { code: String(e.code ?? 'UNKNOWN'), message: String(e.message ?? 'The interviewer hit a problem.'), retryable: Boolean(e.retryable) },
      };
    }
    default:
      return state;
  }
}

/** The question currently being asked (questions are ordered by position; currentIndex indexes that list). */
export function currentQuestion(detail: SessionDetail | null): SessionQuestion | undefined {
  if (!detail) return undefined;
  const sorted = [...detail.questions].sort((a, b) => a.position - b.position);
  return sorted[detail.session.currentIndex] ?? sorted[sorted.length - 1];
}
