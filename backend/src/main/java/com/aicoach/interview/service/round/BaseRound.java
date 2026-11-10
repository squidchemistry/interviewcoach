package com.aicoach.interview.service.round;

import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.common.error.AppException;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.TurnIntent;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.service.engine.SessionContext;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.service.QuestionService;
import com.aicoach.user.domain.TargetRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template Method base for rounds: the shared algorithm for choosing questions, building interviewer prompts,
 * assessing answers and deciding the next step, with hooks that each round overrides.
 */
public abstract class BaseRound implements InterviewRound {

    private static final Logger log = LoggerFactory.getLogger(BaseRound.class);
    protected static final int MAX_FOLLOW_UPS = 2;

    protected final AiGateway ai;
    protected final QuestionService questions;
    protected final QuestionSelector selector;
    protected final DifficultyPolicy difficultyPolicy;
    protected final ObjectMapper json;

    protected BaseRound(AiGateway ai, QuestionService questions, QuestionSelector selector,
            DifficultyPolicy difficultyPolicy, ObjectMapper json) {
        this.ai = ai;
        this.questions = questions;
        this.selector = selector;
        this.difficultyPolicy = difficultyPolicy;
        this.json = json;
    }

    // ------------------------------------------------------------------ hooks

    /** Bank question type, or null for rounds that generate their own questions. */
    protected abstract QuestionType questionType();

    protected abstract String modeLabel();

    /** Extra prompt variables for this round (e.g. expected complexity, focus areas). */
    protected void addModeVariables(Map<String, Object> vars, SessionContext ctx, Question bankQuestion) {
    }

    /** What the candidate's UI needs for this question (e.g. DSA signature and starter code). */
    protected JsonNode questionContext(Question question) {
        return json.valueToTree(questions.publicView(question));
    }

    protected boolean wantsFollowUp(SessionContext ctx, TurnAssessment assessment) {
        return true;
    }

    /** Lets a round blend objective signals into the turn score (e.g. DSA test results). */
    protected double adjustScore(SessionContext ctx, double score) {
        return score;
    }

    protected void addAssessmentVariables(Map<String, Object> vars, SessionContext ctx) {
    }

    // ------------------------------------------------------------------ template methods

    @Override
    public PlannedQuestion pickQuestion(SessionContext ctx, Difficulty difficulty, int position) {
        Set<UUID> used = new HashSet<>();
        ctx.questions().forEach(q -> {
            if (q.getQuestionId() != null) {
                used.add(q.getQuestionId());
            }
        });
        Question question = selector.select(questionType(), difficulty, ctx.session(), used, position);
        return new PlannedQuestion(question.getId(), question.getTitle(), question.getBodyMd(), question.getDifficulty(),
                question.getTags(), questionContext(question));
    }

    @Override
    public Map<String, Object> interviewerVariables(SessionContext ctx, TurnIntent intent, String focus) {
        SessionQuestion current = ctx.current();
        Map<String, Object> vars = baseVariables(ctx);
        vars.put("intent", intent.name());
        vars.put("focus", focus);
        vars.put("isFirst", current.getPosition() == 0 && intent == TurnIntent.ASK);
        vars.put("conversation", conversation(ctx));
        vars.put("lastAnswer", lastAnswer(ctx));
        vars.put("followUps", current.getFollowUps());
        Question bank = bankQuestion(current);
        if (bank != null) {
            vars.put("references", references(bank));
        }
        addModeVariables(vars, ctx, bank);
        return vars;
    }

    @Override
    public TurnAssessment assess(SessionContext ctx) {
        SessionQuestion current = ctx.current();
        Map<String, Object> vars = baseVariables(ctx);
        vars.put("expectedPoints", expectedPoints(ctx));
        vars.put("answers", answers(ctx));
        vars.put("followUps", current.getFollowUps());
        vars.put("outputSchema", TurnAssessment.SCHEMA);
        addAssessmentVariables(vars, ctx);
        TurnAssessment assessment;
        try {
            assessment = ai.structured("turn-assessment", vars, TurnAssessment.class, callContext(ctx, AiPurpose.TURN_ASSESSMENT))
                    .value();
        } catch (AppException e) {
            log.warn("Turn assessment failed ({}); using the rule-based fallback", e.getMessage());
            assessment = fallbackAssessment(ctx);
        }
        double adjusted = Math.max(0, Math.min(10, adjustScore(ctx, assessment.score())));
        return new TurnAssessment(Math.round(adjusted * 10) / 10.0, assessment.coveredPoints(), assessment.missingPoints(),
                assessment.followUpRecommended(), assessment.followUpFocus(), assessment.star(), assessment.integrityFlags());
    }

    @Override
    public NextAction decideNext(SessionContext ctx, TurnAssessment assessment) {
        SessionQuestion current = ctx.current();
        if (assessment.followUpRecommended() && current.getFollowUps() < MAX_FOLLOW_UPS && wantsFollowUp(ctx, assessment)) {
            String focus = assessment.followUpFocus() != null && !assessment.followUpFocus().isBlank()
                    ? assessment.followUpFocus()
                    : assessment.missingPoints().isEmpty() ? "depth" : assessment.missingPoints().getFirst();
            return new NextAction.FollowUp(focus);
        }
        if (ctx.session().getCurrentIndex() + 1 < ctx.session().getPlannedQuestions()) {
            DifficultyPolicy.Decision decision = difficultyPolicy.next(ctx.session().getCurrentDifficulty(), assessment.score());
            return new NextAction.NextQuestion(decision.difficulty(), decision.reason());
        }
        return new NextAction.Finish();
    }

    // ------------------------------------------------------------------ helpers for subclasses

    protected Map<String, Object> baseVariables(SessionContext ctx) {
        SessionQuestion current = ctx.current();
        Map<String, Object> vars = new HashMap<>();
        InterviewMode mode = mode();
        vars.put("mode", mode.name());
        vars.put("modeLabel", modeLabel());
        vars.put("roleLabel", roleLabel(ctx.session().getTargetRole()));
        vars.put("company", ctx.session().getCompany());
        vars.put("candidateName", ctx.candidateName());
        vars.put("position", current.getPosition() + 1);
        vars.put("planned", ctx.session().getPlannedQuestions());
        vars.put("difficulty", current.getDifficulty().name());
        vars.put("questionTitle", current.getTitle());
        vars.put("questionMd", current.getPromptMd());
        vars.put("isDsa", mode == InterviewMode.DSA);
        vars.put("isLld", mode == InterviewMode.LLD);
        vars.put("isBehavioural", mode == InterviewMode.BEHAVIOURAL);
        vars.put("isResume", mode == InterviewMode.RESUME_DEEP_DIVE);
        return vars;
    }

    public CallContext callContext(SessionContext ctx, AiPurpose purpose) {
        return new CallContext(ctx.session().getUserId(), ctx.session().getId(), purpose,
                ctx.session().getSeed() + ctx.turns().size());
    }

    protected Question bankQuestion(SessionQuestion current) {
        return current.getQuestionId() == null ? null : questions.require(current.getQuestionId());
    }

    protected List<String> expectedPoints(SessionContext ctx) {
        SessionQuestion current = ctx.current();
        Question bank = bankQuestion(current);
        if (bank != null) {
            return questions.expectedPoints(bank);
        }
        List<String> signals = new ArrayList<>();
        current.getContext().path("expectedSignals").forEach(n -> signals.add(n.asText()));
        return signals;
    }

    /** All candidate answers for the current question, including code/diagram attachments. */
    protected String answers(SessionContext ctx) {
        return ctx.turnsForCurrent().stream().filter(t -> t.getRole() == TurnRole.CANDIDATE)
                .map(BaseRound::withAttachments).collect(Collectors.joining("\n\n---\n\n"));
    }

    protected String lastAnswer(SessionContext ctx) {
        List<Turn> turns = ctx.turnsForCurrent();
        for (int i = turns.size() - 1; i >= 0; i--) {
            if (turns.get(i).getKind() == TurnKind.ANSWER) {
                return turns.get(i).getContent();
            }
        }
        return "";
    }

    protected String conversation(SessionContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (Turn turn : ctx.turnsForCurrent()) {
            String who = switch (turn.getRole()) {
                case INTERVIEWER -> "Interviewer";
                case CANDIDATE -> "Candidate";
                case SYSTEM -> "System";
            };
            sb.append(who).append(" (").append(turn.getKind().name().toLowerCase()).append("): ")
                    .append(turn.getRole() == TurnRole.CANDIDATE ? withAttachments(turn) : turn.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    static String withAttachments(Turn turn) {
        StringBuilder sb = new StringBuilder(turn.getContent());
        JsonNode meta = turn.getMetadata();
        if (meta.hasNonNull("code")) {
            sb.append("\n\nCandidate code (").append(meta.path("language").asText("")).append("):\n```\n")
                    .append(meta.get("code").asText()).append("\n```");
        }
        if (meta.hasNonNull("mermaid")) {
            sb.append("\n\nCandidate class diagram (Mermaid):\n```mermaid\n").append(meta.get("mermaid").asText()).append("\n```");
        }
        return sb.toString();
    }

    private String references(Question bank) {
        return questions.neighbours(bank.getId(), 3).stream()
                .map(q -> "- " + q.getTitle() + " (" + String.join(", ", q.getTags()) + ")")
                .collect(Collectors.joining("\n"));
    }

    /** Rule-based fallback when the AI assessment is unavailable: the conversation never stalls. */
    private TurnAssessment fallbackAssessment(SessionContext ctx) {
        String text = answers(ctx);
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        double score = Math.min(8.0, 2.0 + words / 30.0);
        return new TurnAssessment(score, List.of(), List.of(), false, null, null, List.of());
    }

    static String roleLabel(TargetRole role) {
        return role == TargetRole.SDE_2 ? "SDE-2 (mid-level software engineer)" : "SDE-1 (entry-level software engineer)";
    }
}
