package com.aicoach.interview.service.round;

import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.service.engine.SessionContext;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.dto.QuestionDetails.BehaviouralDetail;
import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.question.dto.QuestionDetails.LldDetail;
import com.aicoach.question.service.QuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The bank-question rounds: DSA, LLD and behavioural (Strategy implementations). */
public final class Rounds {

    private Rounds() {
    }

    /** Coding round: test results and hints feed the turn score; follow-ups probe complexity and edge cases. */
    @Component
    public static class DsaRound extends BaseRound {

        public DsaRound(AiGateway ai, QuestionService questions, QuestionSelector selector, DifficultyPolicy policy,
                ObjectMapper json) {
            super(ai, questions, selector, policy, json);
        }

        @Override
        public InterviewMode mode() {
            return InterviewMode.DSA;
        }

        @Override
        protected QuestionType questionType() {
            return QuestionType.DSA;
        }

        @Override
        protected String modeLabel() {
            return "coding (DSA)";
        }

        @Override
        public boolean supportsHints() {
            return true;
        }

        @Override
        public String seedHint(SessionContext ctx, int level) {
            Question bank = bankQuestion(ctx.current());
            return bank == null ? null : questions.dsa(bank).hints().get(level - 1);
        }

        @Override
        protected void addModeVariables(Map<String, Object> vars, SessionContext ctx, Question bank) {
            if (bank != null) {
                DsaDetail detail = questions.dsa(bank);
                vars.put("expectedTime", detail.expectedTimeComplexity());
                vars.put("expectedSpace", detail.expectedSpaceComplexity());
            }
            vars.put("codeResult", codeSummary(ctx));
        }

        @Override
        protected void addAssessmentVariables(Map<String, Object> vars, SessionContext ctx) {
            vars.put("codeSummary", codeSummary(ctx));
            Map<String, Object> code = ctx.latestCode();
            if (code != null && Boolean.TRUE.equals(code.get("executed"))) {
                vars.put("testsPassed", code.get("passed"));
                vars.put("testsTotal", code.get("total"));
            }
        }

        /** 60 % AI judgement + 40 % test pass rate (when code really ran), minus 0.5 per hint level used. */
        @Override
        protected double adjustScore(SessionContext ctx, double score) {
            Map<String, Object> code = ctx.latestCode();
            double adjusted = score;
            if (code != null && Boolean.TRUE.equals(code.get("executed"))) {
                int total = ((Number) code.get("total")).intValue();
                int passed = ((Number) code.get("passed")).intValue();
                if (total > 0) {
                    adjusted = 0.6 * score + 0.4 * 10.0 * passed / total;
                }
            }
            return adjusted - 0.5 * ctx.current().getHintsUsed();
        }

        private static String codeSummary(SessionContext ctx) {
            Map<String, Object> code = ctx.latestCode();
            if (code == null) {
                return null;
            }
            if (!Boolean.TRUE.equals(code.get("executed"))) {
                return "code execution is disabled on this server; correctness could not be measured";
            }
            return code.get("kind") + " verdict=" + code.get("verdict") + ", passed " + code.get("passed") + "/" + code.get("total");
        }
    }

    /** Low-level design round: follow-ups rotate through concurrency, extensibility, patterns and SOLID. */
    @Component
    public static class LldRound extends BaseRound {

        public LldRound(AiGateway ai, QuestionService questions, QuestionSelector selector, DifficultyPolicy policy,
                ObjectMapper json) {
            super(ai, questions, selector, policy, json);
        }

        @Override
        public InterviewMode mode() {
            return InterviewMode.LLD;
        }

        @Override
        protected QuestionType questionType() {
            return QuestionType.LLD;
        }

        @Override
        protected String modeLabel() {
            return "low-level design";
        }

        @Override
        protected void addModeVariables(Map<String, Object> vars, SessionContext ctx, Question bank) {
            if (bank != null) {
                LldDetail detail = questions.lld(bank);
                vars.put("focusAreas", String.join(", ", detail.focusAreas()));
                vars.put("requirements", String.join("; ", detail.requirements()));
            }
        }
    }

    /** STAR behavioural round: follow-ups target the weakest STAR component. */
    @Component
    public static class BehaviouralRound extends BaseRound {

        public BehaviouralRound(AiGateway ai, QuestionService questions, QuestionSelector selector,
                DifficultyPolicy policy, ObjectMapper json) {
            super(ai, questions, selector, policy, json);
        }

        @Override
        public InterviewMode mode() {
            return InterviewMode.BEHAVIOURAL;
        }

        @Override
        protected QuestionType questionType() {
            return QuestionType.BEHAVIOURAL;
        }

        @Override
        protected String modeLabel() {
            return "behavioural";
        }

        @Override
        protected void addModeVariables(Map<String, Object> vars, SessionContext ctx, Question bank) {
            if (bank != null) {
                BehaviouralDetail detail = questions.behavioural(bank);
                vars.put("whatGoodLooksLike", detail.whatGoodLooksLike());
                vars.put("followUpProbes", String.join("; ", detail.followUpProbes()));
                vars.put("leadershipPrinciple", detail.leadershipPrinciple());
            }
        }

        @Override
        protected boolean wantsFollowUp(SessionContext ctx, TurnAssessment assessment) {
            if (assessment.star() == null) {
                return true;
            }
            var s = assessment.star();
            return Math.min(Math.min(s.situation(), s.task()), Math.min(s.action(), s.result())) < 7;
        }
    }
}
