package com.aicoach.interview.service.engine;

import com.aicoach.interview.domain.InterviewEnums.SessionStage;
import com.aicoach.interview.domain.InterviewSession;
import org.springframework.stereotype.Component;

/** Factory: the state object for a session's persisted status/stage. States are stateless singletons. */
@Component
public class SessionStateFactory {

    private static final SessionState CREATED = new SessionStates.Created();
    private static final SessionState QUESTION = new SessionStates.Generating(SessionStage.QUESTION);
    private static final SessionState FOLLOW_UP = new SessionStates.Generating(SessionStage.FOLLOW_UP);
    private static final SessionState ANSWER = new SessionStates.Answering();
    private static final SessionState EVALUATING = new SessionStates.Evaluating();
    private static final SessionState COMPLETED = new SessionStates.Completed();
    private static final SessionState ABANDONED = new SessionStates.Abandoned();

    public SessionState forSession(InterviewSession session) {
        return switch (session.getStatus()) {
            case CREATED -> CREATED;
            case IN_PROGRESS -> switch (session.getStage()) {
                case QUESTION -> QUESTION;
                case FOLLOW_UP -> FOLLOW_UP;
                case ANSWER -> ANSWER;
            };
            case EVALUATING -> EVALUATING;
            case COMPLETED -> COMPLETED;
            case ABANDONED -> ABANDONED;
        };
    }
}
