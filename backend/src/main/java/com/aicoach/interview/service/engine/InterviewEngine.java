package com.aicoach.interview.service.engine;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.repository.InterviewJdbcRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository.CodeSubmissionSummary;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.interview.repository.SessionRepository;
import com.aicoach.interview.repository.TurnRepository;
import com.aicoach.interview.service.round.RoundFactory;
import com.aicoach.interview.service.stream.SessionEventStream;
import com.aicoach.user.domain.User;
import com.aicoach.user.repository.UserRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Facade over the interview state machine. Every change to a session — user command or completion of background work —
 * goes through {@link #dispatch}: load the aggregate, let the current {@link SessionState} apply the command, commit
 * (optimistic locking on the session row), then publish SSE events and start the requested {@link Effect}s.
 */
@Service
public class InterviewEngine {

    private static final Logger log = LoggerFactory.getLogger(InterviewEngine.class);
    private static final int MAX_ATTEMPTS = 4;

    private final SessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;
    private final TurnRepository turns;
    private final InterviewJdbcRepository jdbc;
    private final UserRepository users;
    private final RoundFactory rounds;
    private final SessionStateFactory states;
    private final SessionEventStream stream;
    private final EffectRunner effects;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final Clock clock;

    public InterviewEngine(SessionRepository sessions, SessionQuestionRepository sessionQuestions, TurnRepository turns,
            InterviewJdbcRepository jdbc, UserRepository users, RoundFactory rounds, SessionStateFactory states,
            SessionEventStream stream, EffectRunner effects, PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.turns = turns;
        this.jdbc = jdbc;
        this.users = users;
        this.rounds = rounds;
        this.states = states;
        this.stream = stream;
        this.effects = effects;
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.clock = clock;
        effects.bind(this);
    }

    /** The committed result of a command. {@code duplicate} = an idempotent replay that changed nothing. */
    public record Outcome(InterviewSession session, boolean duplicate) {
    }

    /**
     * Applies {@code command}. {@code userId} is the acting user for user commands (ownership is checked: other users
     * get 404) or null for internal commands. Retries on optimistic-lock conflicts with the fresh state.
     */
    public Outcome dispatch(UUID sessionId, UUID userId, SessionCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                Applied applied = tx.execute(status -> apply(sessionId, userId, command));
                afterCommit(applied);
                return new Outcome(applied.session(), applied.duplicate());
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                // a concurrent command won (version bump, or the same turn seq / client message id): re-run on fresh state
                if (attempt >= MAX_ATTEMPTS) {
                    throw new AppException(ErrorCode.STATE_CONFLICT, "The session changed concurrently; please retry");
                }
                log.debug("Optimistic lock conflict on session {} (attempt {})", sessionId, attempt);
            }
        }
    }

    /** Read-only snapshot of a session's context for background work (AI calls happen outside any transaction). */
    public SessionContext loadContext(UUID sessionId) {
        return readTx.execute(status -> context(requireSession(sessionId, null)));
    }

    // ------------------------------------------------------------------ internals

    private record Applied(InterviewSession session, List<Object[]> events, List<Effect> effects, boolean duplicate) {
    }

    private Applied apply(UUID sessionId, UUID userId, SessionCommand command) {
        InterviewSession session = requireSession(sessionId, userId);
        if (command instanceof SessionCommand.SubmitAnswer submit && submit.clientMessageId() != null
                && turns.findBySessionIdAndClientMessageId(sessionId, submit.clientMessageId()).isPresent()) {
            return new Applied(session, List.of(), List.of(), true);
        }
        SessionContext ctx = context(session);
        SessionState state = states.forSession(session);
        switch (command) {
            case SessionCommand.Start c -> state.start(ctx, c);
            case SessionCommand.SubmitAnswer c -> state.submitAnswer(ctx, c);
            case SessionCommand.RequestHint c -> state.requestHint(ctx, c);
            case SessionCommand.RetryTurn c -> state.retryTurn(ctx, c);
            case SessionCommand.EndEarly c -> state.endEarly(ctx, c);
            case SessionCommand.Abandon c -> state.abandon(ctx, c);
            case SessionCommand.TurnDelivered c -> state.turnDelivered(ctx, c);
            case SessionCommand.GenerationFailed c -> state.generationFailed(ctx, c);
            case SessionCommand.Decide c -> state.decide(ctx, c);
            case SessionCommand.Timeout c -> state.timeout(ctx, c);
            case SessionCommand.BudgetExhausted c -> state.budgetExhausted(ctx, c);
            case SessionCommand.EvaluationFinished c -> state.evaluationFinished(ctx, c);
            case SessionCommand.CodeRan c -> state.codeRan(ctx, c);
            case SessionCommand.RecordIntegrity c -> state.recordIntegrity(ctx, c);
        }
        InterviewSession saved = sessions.saveAndFlush(session);
        return new Applied(saved, ctx.events(), ctx.effects(), false);
    }

    private void afterCommit(Applied applied) {
        if (applied.duplicate()) {
            return;
        }
        UUID id = applied.session().getId();
        for (Object[] event : applied.events()) {
            stream.publish(id, (String) event[0], event[1]);
        }
        stream.publish(id, "state.changed", stateEvent(applied.session()));
        for (Effect effect : applied.effects()) {
            effects.submit(id, effect);
        }
    }

    static Map<String, Object> stateEvent(InterviewSession s) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("status", s.getStatus());
        event.put("stage", s.getStage());
        event.put("processing", s.isProcessing());
        event.put("currentIndex", s.getCurrentIndex());
        event.put("currentDifficulty", s.getCurrentDifficulty());
        event.put("endReason", s.getEndReason());
        event.put("version", s.getVersion());
        return event;
    }

    private InterviewSession requireSession(UUID sessionId, UUID userId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Interview not found"));
        if (userId != null && !session.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Interview not found");
        }
        return session;
    }

    private SessionContext context(InterviewSession session) {
        List<SessionQuestion> questions = sessionQuestions.findBySessionIdOrderByPosition(session.getId());
        List<Turn> transcript = turns.findBySessionIdOrderBySeq(session.getId());
        String name = users.findById(session.getUserId()).map(User::getName).orElse("the candidate");
        Map<String, Object> latestCode = null;
        SessionQuestion current = session.getCurrentIndex() >= 0 && session.getCurrentIndex() < questions.size()
                ? questions.get(session.getCurrentIndex()) : null;
        if (current != null) {
            for (CodeSubmissionSummary s : jdbc.latestSubmissions(session.getId())) {
                if (s.sessionQuestionId().equals(current.getId())) {
                    latestCode = codeMap(s);
                }
            }
        }
        return new SessionContext(session, questions, transcript, rounds.forMode(session.getMode()), name,
                clock.instant(), turns, sessionQuestions, latestCode);
    }

    static Map<String, Object> codeMap(CodeSubmissionSummary s) {
        Map<String, Object> code = new HashMap<>();
        code.put("kind", s.kind());
        code.put("language", s.language());
        code.put("verdict", s.verdict());
        code.put("passed", s.passed());
        code.put("total", s.total());
        code.put("executed", !"NOT_EXECUTED".equals(s.verdict()));
        code.put("source", s.source());
        return code;
    }
}
