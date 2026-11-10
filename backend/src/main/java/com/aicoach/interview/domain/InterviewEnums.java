package com.aicoach.interview.domain;

/** Enumerations of the interview engine. */
public final class InterviewEnums {

    private InterviewEnums() {
    }

    public enum InterviewMode { DSA, LLD, BEHAVIOURAL, RESUME_DEEP_DIVE }

    /** Top-level state. IN_PROGRESS has the sub-state {@link SessionStage}. */
    public enum SessionStatus { CREATED, IN_PROGRESS, EVALUATING, COMPLETED, ABANDONED }

    public enum SessionStage { QUESTION, ANSWER, FOLLOW_UP }

    public enum EndReason { COMPLETED_ALL, ENDED_BY_USER, INACTIVITY, ABANDONED_BY_USER, BUDGET_EXHAUSTED }

    public enum TurnRole { INTERVIEWER, CANDIDATE, SYSTEM }

    public enum TurnKind { QUESTION, ANSWER, FOLLOW_UP, HINT, CODE_RUN, SYSTEM_NOTICE }

    public enum ContentFormat { TEXT, MARKDOWN, CODE, MERMAID }

    public enum InputMode { TEXT, VOICE }

    public enum TurnIntent { ASK, FOLLOW_UP }

    public enum EvaluationStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
}
