package com.aicoach.interview.service.engine;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
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

/**
 * State pattern: the session's behaviour depends on its state. Each state object handles only the commands that are
 * legal in that state; everything else falls through to {@link BaseState} and becomes 409 INVALID_TRANSITION.
 * States mutate the aggregate through {@link SessionContext} and request asynchronous work as {@link Effect}s.
 */
public interface SessionState {

    String name();

    void start(SessionContext ctx, Start command);

    void submitAnswer(SessionContext ctx, SubmitAnswer command);

    void requestHint(SessionContext ctx, RequestHint command);

    void retryTurn(SessionContext ctx, RetryTurn command);

    void endEarly(SessionContext ctx, EndEarly command);

    void abandon(SessionContext ctx, Abandon command);

    void turnDelivered(SessionContext ctx, TurnDelivered command);

    void generationFailed(SessionContext ctx, GenerationFailed command);

    void decide(SessionContext ctx, Decide command);

    void timeout(SessionContext ctx, Timeout command);

    void budgetExhausted(SessionContext ctx, BudgetExhausted command);

    void evaluationFinished(SessionContext ctx, EvaluationFinished command);

    void codeRan(SessionContext ctx, CodeRan command);

    void recordIntegrity(SessionContext ctx, RecordIntegrity command);

    /** Default: every command is illegal. */
    abstract class BaseState implements SessionState {

        protected AppException illegal(String command) {
            return new AppException(ErrorCode.INVALID_TRANSITION, "Cannot " + command + " while the session is " + name());
        }

        @Override
        public void start(SessionContext ctx, Start command) {
            throw illegal("start");
        }

        @Override
        public void submitAnswer(SessionContext ctx, SubmitAnswer command) {
            throw illegal("submit an answer");
        }

        @Override
        public void requestHint(SessionContext ctx, RequestHint command) {
            throw illegal("request a hint");
        }

        @Override
        public void retryTurn(SessionContext ctx, RetryTurn command) {
            throw illegal("retry the interviewer's turn");
        }

        @Override
        public void endEarly(SessionContext ctx, EndEarly command) {
            throw illegal("end the interview");
        }

        @Override
        public void abandon(SessionContext ctx, Abandon command) {
            throw illegal("abandon the interview");
        }

        @Override
        public void turnDelivered(SessionContext ctx, TurnDelivered command) {
            throw illegal("deliver a turn");
        }

        @Override
        public void generationFailed(SessionContext ctx, GenerationFailed command) {
            throw illegal("record a generation failure");
        }

        @Override
        public void decide(SessionContext ctx, Decide command) {
            throw illegal("decide the next step");
        }

        @Override
        public void timeout(SessionContext ctx, Timeout command) {
            throw illegal("time out");
        }

        @Override
        public void budgetExhausted(SessionContext ctx, BudgetExhausted command) {
            throw illegal("exhaust the budget");
        }

        @Override
        public void evaluationFinished(SessionContext ctx, EvaluationFinished command) {
            throw illegal("finish evaluation");
        }

        @Override
        public void codeRan(SessionContext ctx, CodeRan command) {
            throw illegal("run code");
        }

        @Override
        public void recordIntegrity(SessionContext ctx, RecordIntegrity command) {
            // accepted in every state; ignored once the session is over
        }
    }
}
