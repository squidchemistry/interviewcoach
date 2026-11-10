package com.aicoach.interview.service.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewEnums.EndReason;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStage;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.interview.repository.TurnRepository;
import com.aicoach.interview.service.round.InterviewRound;
import com.aicoach.interview.service.round.NextAction;
import com.aicoach.interview.service.round.PlannedQuestion;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.user.domain.TargetRole;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The transition table of the interview state machine, without a database. */
class SessionStatesTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    final SessionStateFactory factory = new SessionStateFactory();
    final TurnRepository turns = mock(TurnRepository.class);
    final SessionQuestionRepository questions = mock(SessionQuestionRepository.class);
    final InterviewRound round = mock(InterviewRound.class);
    InterviewSession session;
    List<SessionQuestion> savedQuestions;
    List<Turn> savedTurns;

    @BeforeEach
    void setUp() {
        session = new InterviewSession(UUID.randomUUID(), InterviewMode.DSA, TargetRole.SDE_1, null, null, null,
                UUID.randomUUID(), false, 2, Difficulty.EASY, 42L);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        savedQuestions = new ArrayList<>();
        savedTurns = new ArrayList<>();
        when(questions.save(any())).thenAnswer(inv -> {
            SessionQuestion q = inv.getArgument(0);
            ReflectionTestUtils.setField(q, "id", UUID.randomUUID());
            savedQuestions.add(q);
            return q;
        });
        when(turns.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(round.pickQuestion(any(), any(), anyInt())).thenAnswer(inv -> new PlannedQuestion(UUID.randomUUID(),
                "Q" + inv.getArgument(2), "body", inv.getArgument(1), List.of("arrays"), JsonNodeFactory.instance.objectNode()));
        when(round.supportsHints()).thenReturn(true);
    }

    SessionContext ctx() {
        return new SessionContext(session, savedQuestions, savedTurns, round, "Asha", NOW, turns, questions, null);
    }

    /** Applies a command and keeps the transcript, like the engine would after commit. */
    SessionContext apply(java.util.function.BiConsumer<SessionState, SessionContext> command) {
        SessionContext ctx = ctx();
        command.accept(factory.forSession(session), ctx);
        savedTurns = new ArrayList<>(ctx.turns());
        return ctx;
    }

    void startAndDeliver() {
        apply((s, c) -> s.start(c, new SessionCommand.Start()));
        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.QUESTION, "Q?")));
    }

    SessionContext answer() {
        return apply((s, c) -> s.submitAnswer(c, new SessionCommand.SubmitAnswer(UUID.randomUUID(), "my answer",
                InputMode.TEXT, "def f(): pass", "PYTHON", null)));
    }

    static TurnAssessment assessment(double score) {
        return new TurnAssessment(score, List.of("a"), List.of("b"), false, null, null, List.of());
    }

    @Test
    void startAsksTheFirstQuestionAndStreamsIt() {
        SessionContext ctx = apply((s, c) -> s.start(c, new SessionCommand.Start()));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStage()).isEqualTo(SessionStage.QUESTION);
        assertThat(session.isProcessing()).isTrue();
        assertThat(session.getCurrentIndex()).isZero();
        assertThat(ctx.effects()).containsExactly(new Effect.Generate(TurnKind.QUESTION, null));
        assertThat(ctx.events()).extracting(e -> e[0]).containsExactly("question.changed");

        assertThatThrownBy(() -> apply((s, c) -> s.start(c, new SessionCommand.Start())))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
    }

    @Test
    void answersAreRejectedUntilTheQuestionIsDeliveredAndWhileProcessing() {
        assertThatThrownBy(this::answer).extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
        apply((s, c) -> s.start(c, new SessionCommand.Start()));
        assertThatThrownBy(this::answer).extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.QUESTION, "Q?")));
        assertThat(session.getStage()).isEqualTo(SessionStage.ANSWER);
        assertThat(session.isProcessing()).isFalse();

        SessionContext ctx = answer();
        assertThat(ctx.effects()).containsExactly(new Effect.Assess());
        assertThat(savedTurns.getLast().getMetadata().get("language").asText()).isEqualTo("PYTHON");
        assertThatThrownBy(this::answer).extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void hintsAreOrderedAndRecorded() {
        startAndDeliver();
        assertThatThrownBy(() -> apply((s, c) -> s.requestHint(c, new SessionCommand.RequestHint(2))))
                .extracting("errorCode").isEqualTo(ErrorCode.HINT_ORDER);
        SessionContext ctx = apply((s, c) -> s.requestHint(c, new SessionCommand.RequestHint(1)));
        assertThat(ctx.effects()).containsExactly(new Effect.GenerateHint(1));
        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.HINT, "Think stack")));
        assertThat(savedQuestions.getFirst().getHintsUsed()).isEqualTo(1);
        assertThat(session.isProcessing()).isFalse();

        when(round.supportsHints()).thenReturn(false);
        assertThatThrownBy(() -> apply((s, c) -> s.requestHint(c, new SessionCommand.RequestHint(2))))
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_FOR_MODE);
    }

    @Test
    void decideFollowsUpThenMovesOnThenFinishes() {
        startAndDeliver();
        answer();
        when(round.decideNext(any(), any())).thenReturn(new NextAction.FollowUp("complexity"));
        SessionContext ctx = apply((s, c) -> s.decide(c, new SessionCommand.Decide(assessment(6))));
        assertThat(session.getStage()).isEqualTo(SessionStage.FOLLOW_UP);
        assertThat(savedQuestions.getFirst().getFollowUps()).isEqualTo(1);
        assertThat(ctx.effects()).containsExactly(new Effect.Generate(TurnKind.FOLLOW_UP, "complexity"));

        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.FOLLOW_UP, "Why?")));
        answer();
        when(round.decideNext(any(), any())).thenReturn(new NextAction.NextQuestion(Difficulty.MEDIUM, "Strong answer"));
        ctx = apply((s, c) -> s.decide(c, new SessionCommand.Decide(assessment(8))));
        assertThat(session.getCurrentIndex()).isEqualTo(1);
        assertThat(session.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(savedQuestions.get(1).getDifficultyReason()).isEqualTo("Strong answer");
        assertThat(ctx.events()).extracting(e -> e[0]).containsExactly("assessment", "question.changed");

        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.QUESTION, "Q2")));
        answer();
        when(round.decideNext(any(), any())).thenReturn(new NextAction.Finish());
        ctx = apply((s, c) -> s.decide(c, new SessionCommand.Decide(assessment(7))));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.EVALUATING);
        assertThat(session.getEndReason()).isEqualTo(EndReason.COMPLETED_ALL);
        assertThat(ctx.effects()).containsExactly(new Effect.Evaluate());

        apply((s, c) -> s.evaluationFinished(c, new SessionCommand.EvaluationFinished(false)));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.EVALUATING);
        apply((s, c) -> s.evaluationFinished(c, new SessionCommand.EvaluationFinished(true)));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThatThrownBy(this::answer).extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
        apply((s, c) -> s.recordIntegrity(c, new SessionCommand.RecordIntegrity(List.of("late signal"))));
        assertThat(session.getIntegrity()).isEmpty();
    }

    @Test
    void runningOutOfQuestionsWrapsUp() {
        startAndDeliver();
        answer();
        when(round.decideNext(any(), any())).thenReturn(new NextAction.NextQuestion(Difficulty.HARD, "up"));
        when(round.pickQuestion(any(), any(), anyInt()))
                .thenThrow(new AppException(ErrorCode.NO_QUESTIONS_AVAILABLE, "none"));
        apply((s, c) -> s.decide(c, new SessionCommand.Decide(assessment(9))));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.EVALUATING);
        assertThat(savedTurns.getLast().getContent()).isEqualTo(SessionStates.WRAP_UP);
    }

    @Test
    void endingWithoutAnswersAbandons() {
        startAndDeliver();
        apply((s, c) -> s.endEarly(c, new SessionCommand.EndEarly()));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.getEndReason()).isEqualTo(EndReason.ABANDONED_BY_USER);
    }

    @Test
    void timeoutsEvaluateWhatWasAnsweredAndIntegrityIsRecorded() {
        startAndDeliver();
        answer();
        apply((s, c) -> s.recordIntegrity(c, new SessionCommand.RecordIntegrity(List.of("Pasted 900 characters"))));
        assertThat(session.getIntegrity()).hasSize(1);
        apply((s, c) -> s.timeout(c, new SessionCommand.Timeout()));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.EVALUATING);
        assertThat(session.getEndReason()).isEqualTo(EndReason.INACTIVITY);
        assertThat(savedTurns.getLast().getKind()).isEqualTo(TurnKind.SYSTEM_NOTICE);
    }

    @Test
    void failuresReleaseTheSessionAndRetryReRunsTheStep() {
        apply((s, c) -> s.start(c, new SessionCommand.Start()));
        assertThatThrownBy(() -> apply((s, c) -> s.retryTurn(c, new SessionCommand.RetryTurn())))
                .extracting("errorCode").isEqualTo(ErrorCode.STATE_CONFLICT);
        SessionContext failed = apply((s, c) -> s.generationFailed(c, new SessionCommand.GenerationFailed("AI_UNAVAILABLE", "x")));
        assertThat(session.isProcessing()).isFalse();
        assertThat(failed.events()).extracting(e -> e[0]).containsExactly("error");
        SessionContext retried = apply((s, c) -> s.retryTurn(c, new SessionCommand.RetryTurn()));
        assertThat(retried.effects()).containsExactly(new Effect.Generate(TurnKind.QUESTION, null));

        apply((s, c) -> s.turnDelivered(c, new SessionCommand.TurnDelivered(UUID.randomUUID(), TurnKind.QUESTION, "Q?")));
        assertThatThrownBy(() -> apply((s, c) -> s.retryTurn(c, new SessionCommand.RetryTurn())))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
        answer();
        apply((s, c) -> s.generationFailed(c, new SessionCommand.GenerationFailed("AI_UNAVAILABLE", "x")));
        assertThat(apply((s, c) -> s.retryTurn(c, new SessionCommand.RetryTurn())).effects())
                .containsExactly(new Effect.Assess());

        apply((s, c) -> s.budgetExhausted(c, new SessionCommand.BudgetExhausted()));
        assertThat(session.getEndReason()).isEqualTo(EndReason.BUDGET_EXHAUSTED);
    }

    @Test
    void abandonIsAllowedBeforeAndDuringTheInterview() {
        apply((s, c) -> s.abandon(c, new SessionCommand.Abandon()));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThatThrownBy(() -> apply((s, c) -> s.abandon(c, new SessionCommand.Abandon())))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TRANSITION);
        assertThat(factory.forSession(session).name()).isEqualTo("ABANDONED");
    }
}
