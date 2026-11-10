import type { Report, SessionDetail, SessionSummary } from '../api/models';

export const SESSION_ID = '33333333-3333-3333-3333-333333333333';

export function makeSummary(overrides: Partial<SessionSummary> = {}): SessionSummary {
  return {
    id: SESSION_ID,
    mode: 'BEHAVIOURAL',
    status: 'IN_PROGRESS',
    stage: 'ANSWER',
    processing: false,
    targetRole: 'SDE_1',
    plannedQuestions: 3,
    currentIndex: 0,
    currentDifficulty: 'EASY',
    voiceEnabled: false,
    createdAt: '2026-11-05T10:00:00Z',
    startedAt: '2026-11-05T10:00:05Z',
    ...overrides,
  };
}

export function makeDetail(overrides: Partial<SessionDetail> = {}, session: Partial<SessionSummary> = {}): SessionDetail {
  return {
    session: makeSummary(session),
    questions: [
      {
        id: 'q-1',
        position: 0,
        title: 'Tell me about a conflict',
        promptMd: 'Describe a time you **disagreed** with a teammate.',
        difficulty: 'EASY',
        difficultyReason: 'Starting at your role level',
        tags: ['conflict'],
        context: { behavioural: { competency: 'CONFLICT_RESOLUTION' } },
        hintsUsed: 0,
        followUps: 0,
        completed: false,
      },
    ],
    transcript: [
      { id: 't-1', seq: 1, role: 'INTERVIEWER', kind: 'QUESTION', content: 'Describe a time you **disagreed** with a teammate.', format: 'MARKDOWN', sessionQuestionId: 'q-1' },
    ],
    lastEventId: '1730960000000-1',
    codeExecutionEnabled: false,
    ...overrides,
  };
}

export function makeReport(overrides: Partial<Report['evaluation']> = {}): Report {
  return {
    session: makeSummary({ status: 'COMPLETED', overallScore: 72, endedAt: '2026-11-05T10:30:00Z' }),
    evaluation: {
      status: 'SUCCEEDED',
      attempts: 1,
      overallScore: 72,
      confidence: 'HIGH',
      summary: 'Clear stories with measurable results; tighten the Situation setup.',
      competencies: [
        {
          competency: 'COMMUNICATION',
          label: 'Communication',
          score: 7.5,
          weight: 0.6,
          lowConfidence: false,
          rationale: 'Structured and concise.',
          evidence: [{ quote: 'We cut failed deploys by 80%', turnSeq: 2 }],
          adjustments: [],
        },
        { competency: 'PROBLEM_SOLVING', label: 'Problem solving', score: 4, weight: 0.4, lowConfidence: true, evidence: [] },
      ],
      strengths: ['Quantified results', 'Owned the outcome'],
      gaps: ['Vague situation', 'Skipped trade-offs'],
      nextSteps: [
        { action: 'Rewrite two stories with a one-line situation', why: 'Vague situation', resourceType: 'REFLECT' },
        { action: 'Do a mock focused on trade-offs', why: 'Skipped trade-offs', resourceType: 'MOCK', relatedTag: 'conflict' },
        { action: 'Read about the STAR method', why: 'Structure', resourceType: 'READ' },
      ],
      perQuestion: [
        {
          position: 0,
          title: 'Tell me about a conflict',
          difficulty: 'EASY',
          hintsUsed: 0,
          followUps: 1,
          turnScore: 7,
          modelAnswerMd: 'A strong answer **names the disagreement** first.',
          keyMissedPoints: ['What you learned'],
          star: {
            situation: { score: 5, justification: 'Brief' },
            task: { score: 7, justification: 'Clear' },
            action: { score: 8, justification: 'Specific' },
            result: { score: 8, justification: 'Measured', quote: 'cut failed deploys by 80%' },
          },
        },
      ],
      integrity: [{ type: 'CLIENT_SIGNAL', detail: 'Large paste (1200 chars)' }],
      rubric: { key: 'BEHAVIOURAL', version: 1 },
      ...overrides,
    },
    transcript: [
      { id: 't-1', seq: 1, role: 'INTERVIEWER', kind: 'QUESTION', content: 'Describe a conflict.', format: 'MARKDOWN' },
      { id: 't-2', seq: 2, role: 'CANDIDATE', kind: 'ANSWER', content: 'We cut failed deploys by 80%', format: 'TEXT' },
    ],
    shareLinks: [],
  };
}
