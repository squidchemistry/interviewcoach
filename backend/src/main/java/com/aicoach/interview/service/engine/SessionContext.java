package com.aicoach.interview.service.engine;

import com.aicoach.interview.domain.InterviewEnums.ContentFormat;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.interview.repository.TurnRepository;
import com.aicoach.interview.service.round.InterviewRound;
import com.aicoach.interview.service.round.PlannedQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a state or round needs about one session, loaded inside the engine's transaction. Mutations go through
 * helper methods so turns get consecutive sequence numbers and events are published only after commit.
 */
public final class SessionContext {

    private final InterviewSession session;
    private final List<SessionQuestion> questions;
    private final List<Turn> turns;
    private final InterviewRound round;
    private final String candidateName;
    private final Instant now;
    private final TurnRepository turnRepository;
    private final SessionQuestionRepository questionRepository;
    private final List<Effect> effects = new ArrayList<>();
    private final List<Object[]> events = new ArrayList<>();
    private final Map<String, Object> latestCode;

    public SessionContext(InterviewSession session, List<SessionQuestion> questions, List<Turn> turns,
            InterviewRound round, String candidateName, Instant now, TurnRepository turnRepository,
            SessionQuestionRepository questionRepository, Map<String, Object> latestCode) {
        this.session = session;
        this.questions = new ArrayList<>(questions);
        this.turns = new ArrayList<>(turns);
        this.round = round;
        this.candidateName = candidateName;
        this.now = now;
        this.turnRepository = turnRepository;
        this.questionRepository = questionRepository;
        this.latestCode = latestCode;
    }

    public InterviewSession session() {
        return session;
    }

    public InterviewRound round() {
        return round;
    }

    public String candidateName() {
        return candidateName;
    }

    public Instant now() {
        return now;
    }

    public List<SessionQuestion> questions() {
        return questions;
    }

    public List<Turn> turns() {
        return turns;
    }

    /** Latest code run/submit for the current question (DSA): verdict, passed, total, executed, language, source. */
    public Map<String, Object> latestCode() {
        return latestCode;
    }

    public SessionQuestion current() {
        int index = session.getCurrentIndex();
        return index >= 0 && index < questions.size() ? questions.get(index) : null;
    }

    public List<Turn> turnsForCurrent() {
        SessionQuestion current = current();
        if (current == null) {
            return List.of();
        }
        return turns.stream().filter(t -> current.getId().equals(t.getSessionQuestionId())).toList();
    }

    public long answeredCount() {
        return turns.stream().filter(t -> t.getKind() == TurnKind.ANSWER).count();
    }

    public Turn addTurn(UUID id, TurnRole role, TurnKind kind, String content, ContentFormat format, InputMode inputMode,
            UUID clientMessageId, JsonNode metadata) {
        int seq = turns.stream().mapToInt(Turn::getSeq).max().orElse(0) + 1;
        SessionQuestion current = current();
        Turn turn = turnRepository.save(new Turn(id == null ? UUID.randomUUID() : id, session.getId(),
                current == null ? null : current.getId(), seq, role, kind, content, format, inputMode, clientMessageId,
                metadata));
        turns.add(turn);
        return turn;
    }

    public SessionQuestion addQuestion(PlannedQuestion planned, int position, String difficultyReason) {
        SessionQuestion question = questionRepository.save(new SessionQuestion(session.getId(), position,
                planned.questionId(), planned.title(), planned.promptMd(), planned.difficulty(), difficultyReason,
                planned.tags(), planned.context()));
        questions.add(question);
        session.advanceTo(position, planned.difficulty());
        return question;
    }

    public void effect(Effect effect) {
        effects.add(effect);
    }

    public List<Effect> effects() {
        return effects;
    }

    /** Queues an SSE event to publish after commit. */
    public void event(String type, Object payload) {
        events.add(new Object[] {type, payload});
    }

    public List<Object[]> events() {
        return events;
    }
}
