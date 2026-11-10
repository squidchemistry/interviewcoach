package com.aicoach.interview.service.engine;

import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewEnums.ContentFormat;
import com.aicoach.interview.domain.InterviewEnums.EndReason;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStage;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.service.engine.SessionCommand.Abandon;
import com.aicoach.interview.service.engine.SessionCommand.BudgetExhausted;
import com.aicoach.interview.service.engine.SessionCommand.CodeRan;
import com.aicoach.interview.service.engine.SessionCommand.Decide;
import com.aicoach.interview.service.engine.SessionCommand.EndEarly;
import com.aicoach.interview.service.engine.SessionCommand.EvaluationFinished;
import com.aicoach.interview.service.engine.SessionCommand.GenerationFailed;
import com.aicoach.interview.service.engine.SessionCommand.RecordIntegrity;
import com.aicoach.interview.service.engine.SessionCommand.RequestHint;
import com.aicoach.interview.service.engine.SessionCommand.RetryTurn;
import com.aicoach.interview.service.engine.SessionCommand.Start;
import com.aicoach.interview.service.engine.SessionCommand.SubmitAnswer;
import com.aicoach.interview.service.engine.SessionCommand.Timeout;
import com.aicoach.interview.service.engine.SessionCommand.TurnDelivered;
import com.aicoach.interview.service.engine.SessionState.BaseState;
import com.aicoach.interview.service.round.NextAction;
import com.aicoach.interview.service.round.PlannedQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** The concrete states of the interview state machine (see docs/04-lld.md §1 for the transition table). */
public final class SessionStates {

    static final String WRAP_UP = "Thanks — that's all the questions I have for today. I'm putting your feedback together now.";
    private static final ObjectMapper JSON = new ObjectMapper();

    private SessionStates() {
    }

    // ------------------------------------------------------------------ CREATED

    public static final class Created extends BaseState {
        @Override
        public String name() {
            return "CREATED";
        }

        @Override
        public void start(SessionContext ctx, Start command) {
            InterviewSession session = ctx.session();
            PlannedQuestion first = ctx.round().pickQuestion(ctx, session.getCurrentDifficulty(), 0);
            ctx.addQuestion(first, 0, "Starting difficulty for your target role");
            session.enter(SessionStatus.IN_PROGRESS, SessionStage.QUESTION, true, ctx.now());
            ctx.event("question.changed", questionEvent(ctx.current()));
            ctx.effect(new Effect.Generate(TurnKind.QUESTION, null));
        }

        @Override
        public void abandon(SessionContext ctx, Abandon command) {
            ctx.session().enter(SessionStatus.ABANDONED, null, false, ctx.now());
            ctx.session().end(EndReason.ABANDONED_BY_USER);
        }
    }

    // ------------------------------------------------------------------ IN_PROGRESS (shared)

    abstract static class InProgress extends BaseState {

        @Override
        public void endEarly(SessionContext ctx, EndEarly command) {
            finish(ctx, EndReason.ENDED_BY_USER, null);
        }

        @Override
        public void abandon(SessionContext ctx, Abandon command) {
            ctx.session().enter(SessionStatus.ABANDONED, null, false, ctx.now());
            ctx.session().end(EndReason.ABANDONED_BY_USER);
        }

        @Override
        public void timeout(SessionContext ctx, Timeout command) {
            finish(ctx, EndReason.INACTIVITY, "The interview ended after 30 minutes without activity.");
        }

        @Override
        public void budgetExhausted(SessionContext ctx, BudgetExhausted command) {
            finish(ctx, EndReason.BUDGET_EXHAUSTED, "This interview reached its AI usage budget, so it ends here.");
        }

        @Override
        public void codeRan(SessionContext ctx, CodeRan command) {
            Turn turn = ctx.addTurn(null, TurnRole.SYSTEM, TurnKind.CODE_RUN, command.summary(), ContentFormat.TEXT,
                    InputMode.TEXT, null, command.metadata());
            ctx.session().setProcessing(ctx.session().isProcessing(), ctx.now());
            ctx.event("turn.completed", turnEvent(turn));
        }

        @Override
        public void recordIntegrity(SessionContext ctx, RecordIntegrity command) {
            ArrayNode integrity = ctx.session().getIntegrity().isArray()
                    ? (ArrayNode) ctx.session().getIntegrity().deepCopy() : JsonNodeFactory.instance.arrayNode();
            command.signals().stream().limit(50 - Math.min(50, integrity.size())).forEach(integrity::add);
            ctx.session().setIntegrity(integrity);
        }

        /** Ends the interview: evaluation if anything was answered, otherwise abandoned. */
        void finish(SessionContext ctx, EndReason reason, String notice) {
            InterviewSession session = ctx.session();
            SessionQuestion current = ctx.current();
            if (current != null) {
                current.complete(ctx.now());
            }
            if (notice != null) {
                ctx.event("turn.completed", turnEvent(ctx.addTurn(null, TurnRole.SYSTEM, TurnKind.SYSTEM_NOTICE, notice,
                        ContentFormat.TEXT, InputMode.TEXT, null, null)));
            }
            if (ctx.answeredCount() > 0) {
                session.enter(SessionStatus.EVALUATING, null, false, ctx.now());
                session.end(reason);
                ctx.effect(new Effect.Evaluate());
            } else {
                session.enter(SessionStatus.ABANDONED, null, false, ctx.now());
                session.end(reason == EndReason.ENDED_BY_USER ? EndReason.ABANDONED_BY_USER : reason);
            }
        }
    }

    // ------------------------------------------------------------------ QUESTION / FOLLOW_UP (interviewer is speaking)

    public static final class Generating extends InProgress {
        private final SessionStage stage;

        Generating(SessionStage stage) {
            this.stage = stage;
        }

        @Override
        public String name() {
            return "IN_PROGRESS/" + stage;
        }

        @Override
        public void turnDelivered(SessionContext ctx, TurnDelivered command) {
            Turn turn = ctx.addTurn(command.turnId(), TurnRole.INTERVIEWER, command.kind(), command.content(),
                    ContentFormat.MARKDOWN, InputMode.TEXT, null, null);
            ctx.session().enter(SessionStatus.IN_PROGRESS, SessionStage.ANSWER, false, ctx.now());
            ctx.event("turn.completed", turnEvent(turn));
        }

        @Override
        public void generationFailed(SessionContext ctx, GenerationFailed command) {
            ctx.session().setProcessing(false, ctx.now());
            ctx.event("error", Map.of("code", command.code(), "message", command.message(), "retryable", true));
        }

        @Override
        public void retryTurn(SessionContext ctx, RetryTurn command) {
            if (ctx.session().isProcessing()) {
                throw new AppException(ErrorCode.STATE_CONFLICT, "The interviewer is still responding");
            }
            ctx.session().setProcessing(true, ctx.now());
            ctx.effect(new Effect.Generate(stage == SessionStage.QUESTION ? TurnKind.QUESTION : TurnKind.FOLLOW_UP, null));
        }
    }

    // ------------------------------------------------------------------ ANSWER (candidate's turn)

    public static final class Answering extends InProgress {
        @Override
        public String name() {
            return "IN_PROGRESS/ANSWER";
        }

        @Override
        public void submitAnswer(SessionContext ctx, SubmitAnswer command) {
            if (ctx.session().isProcessing()) {
                throw new AppException(ErrorCode.STATE_CONFLICT, "The interviewer is still thinking about your last message");
            }
            ObjectNode metadata = JsonNodeFactory.instance.objectNode();
            if (command.code() != null && !command.code().isBlank()) {
                metadata.put("code", command.code());
                metadata.put("language", command.language());
            }
            if (command.mermaid() != null && !command.mermaid().isBlank()) {
                metadata.put("mermaid", command.mermaid());
            }
            Turn turn = ctx.addTurn(null, TurnRole.CANDIDATE, TurnKind.ANSWER, command.content(), ContentFormat.MARKDOWN,
                    command.inputMode(), command.clientMessageId(), metadata);
            ctx.session().setProcessing(true, ctx.now());
            ctx.event("turn.completed", turnEvent(turn));
            ctx.effect(new Effect.Assess());
        }

        @Override
        public void requestHint(SessionContext ctx, RequestHint command) {
            if (!ctx.round().supportsHints()) {
                throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "Hints are only available in coding rounds");
            }
            if (ctx.session().isProcessing()) {
                throw new AppException(ErrorCode.STATE_CONFLICT, "The interviewer is still responding");
            }
            int expected = ctx.current().getHintsUsed() + 1;
            if (command.level() != expected) {
                throw new AppException(ErrorCode.HINT_ORDER, expected > 3 ? "All three hints have been used"
                        : "Hints are given in order; the next one is level " + expected);
            }
            ctx.session().setProcessing(true, ctx.now());
            ctx.effect(new Effect.GenerateHint(command.level()));
        }

        @Override
        public void turnDelivered(SessionContext ctx, TurnDelivered command) {
            if (command.kind() != TurnKind.HINT) {
                throw illegal("deliver a " + command.kind() + " turn");
            }
            int level = ctx.current().getHintsUsed() + 1;
            ctx.current().useHint(level);
            ObjectNode metadata = JsonNodeFactory.instance.objectNode().put("hintLevel", level);
            Turn turn = ctx.addTurn(command.turnId(), TurnRole.INTERVIEWER, TurnKind.HINT, command.content(),
                    ContentFormat.MARKDOWN, InputMode.TEXT, null, metadata);
            ctx.session().setProcessing(false, ctx.now());
            ctx.event("turn.completed", turnEvent(turn));
        }

        @Override
        public void generationFailed(SessionContext ctx, GenerationFailed command) {
            ctx.session().setProcessing(false, ctx.now());
            ctx.event("error", Map.of("code", command.code(), "message", command.message(), "retryable", true));
        }

        /** Re-runs the assessment of an answer whose assessment failed or stalled. */
        @Override
        public void retryTurn(SessionContext ctx, RetryTurn command) {
            if (ctx.session().isProcessing()) {
                throw new AppException(ErrorCode.STATE_CONFLICT, "The interviewer is still responding");
            }
            var turns = ctx.turnsForCurrent();
            if (turns.isEmpty() || turns.getLast().getKind() != TurnKind.ANSWER) {
                throw new AppException(ErrorCode.INVALID_TRANSITION, "There is nothing to retry: it's your turn to answer");
            }
            ctx.session().setProcessing(true, ctx.now());
            ctx.effect(new Effect.Assess());
        }

        @Override
        public void decide(SessionContext ctx, Decide command) {
            TurnAssessment assessment = command.assessment();
            SessionQuestion current = ctx.current();
            current.recordAssessment(assessment.score(), JSON.valueToTree(assessment));
            if (!assessment.integrityFlags().isEmpty()) {
                recordIntegrity(ctx, new RecordIntegrity(assessment.integrityFlags().stream()
                        .map(f -> "Q" + (current.getPosition() + 1) + ": " + f).toList()));
            }
            NextAction next = ctx.round().decideNext(ctx, assessment);
            switch (next) {
                case NextAction.FollowUp followUp -> {
                    current.incrementFollowUps();
                    ctx.session().enter(SessionStatus.IN_PROGRESS, SessionStage.FOLLOW_UP, true, ctx.now());
                    ctx.effect(new Effect.Generate(TurnKind.FOLLOW_UP, followUp.focus()));
                }
                case NextAction.NextQuestion nextQuestion -> {
                    current.complete(ctx.now());
                    ctx.event("assessment", assessmentEvent(current, nextQuestion.difficulty().name(), nextQuestion.reason()));
                    PlannedQuestion planned;
                    try {
                        planned = ctx.round().pickQuestion(ctx, nextQuestion.difficulty(), current.getPosition() + 1);
                    } catch (AppException e) {
                        if (e.errorCode() != ErrorCode.NO_QUESTIONS_AVAILABLE) {
                            throw e;
                        }
                        wrapUp(ctx);
                        return;
                    }
                    ctx.addQuestion(planned, current.getPosition() + 1, nextQuestion.reason());
                    ctx.session().enter(SessionStatus.IN_PROGRESS, SessionStage.QUESTION, true, ctx.now());
                    ctx.event("question.changed", questionEvent(ctx.current()));
                    ctx.effect(new Effect.Generate(TurnKind.QUESTION, null));
                }
                case NextAction.Finish finish -> {
                    ctx.event("assessment", assessmentEvent(current, null, "Last question completed"));
                    wrapUp(ctx);
                }
            }
        }

        private void wrapUp(SessionContext ctx) {
            ctx.event("turn.completed", turnEvent(ctx.addTurn(null, TurnRole.INTERVIEWER, TurnKind.FOLLOW_UP, WRAP_UP,
                    ContentFormat.MARKDOWN, InputMode.TEXT, null, null)));
            finish(ctx, EndReason.COMPLETED_ALL, null);
        }
    }

    // ------------------------------------------------------------------ EVALUATING / terminal

    public static final class Evaluating extends BaseState {
        @Override
        public String name() {
            return "EVALUATING";
        }

        @Override
        public void evaluationFinished(SessionContext ctx, EvaluationFinished command) {
            if (command.succeeded()) {
                ctx.session().enter(SessionStatus.COMPLETED, null, false, ctx.now());
            }
            ctx.event("evaluation.progress", Map.of("status", command.succeeded() ? "SUCCEEDED" : "FAILED"));
        }
    }

    public static final class Completed extends BaseState {
        @Override
        public String name() {
            return "COMPLETED";
        }
    }

    public static final class Abandoned extends BaseState {
        @Override
        public String name() {
            return "ABANDONED";
        }
    }

    // ------------------------------------------------------------------ event payloads

    static Map<String, Object> turnEvent(Turn turn) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("turnId", turn.getId());
        event.put("seq", turn.getSeq());
        event.put("role", turn.getRole());
        event.put("kind", turn.getKind());
        event.put("content", turn.getContent());
        event.put("format", turn.getFormat());
        event.put("sessionQuestionId", turn.getSessionQuestionId());
        event.put("metadata", turn.getMetadata());
        return event;
    }

    static Map<String, Object> questionEvent(SessionQuestion q) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sessionQuestionId", q.getId());
        event.put("difficultyReason", q.getDifficultyReason());
        event.put("position", q.getPosition());
        event.put("title", q.getTitle());
        event.put("promptMd", q.getPromptMd());
        event.put("difficulty", q.getDifficulty());
        event.put("tags", q.getTags());
        event.put("context", q.getContext());
        return event;
    }

    static Map<String, Object> assessmentEvent(SessionQuestion q, String nextDifficulty, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sessionQuestionId", q.getId());
        event.put("position", q.getPosition());
        event.put("turnScore", q.getTurnScore());
        event.put("nextDifficulty", nextDifficulty);
        event.put("reason", reason);
        return event;
    }
}
