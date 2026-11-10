package com.aicoach.interview.service.evaluation;

import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.schema.EvaluationOutput;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.ai.service.AiGateway.StructuredResult;
import com.aicoach.common.error.AppException;
import com.aicoach.interview.domain.Evaluation;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.repository.EvaluationRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository.CodeSubmissionSummary;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.interview.repository.SessionRepository;
import com.aicoach.interview.repository.TurnRepository;
import com.aicoach.interview.service.evaluation.EvaluationRun.CandidateTurn;
import com.aicoach.interview.service.evaluation.EvaluationRun.CompetencyScore;
import com.aicoach.interview.service.evaluation.EvaluationRun.QuestionSignal;
import com.aicoach.interview.service.stream.SessionEventStream;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.Rubric;
import com.aicoach.question.dto.RubricDefinition;
import com.aicoach.question.service.QuestionService;
import com.aicoach.question.service.RubricService;
import com.aicoach.user.domain.TargetRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Final evaluation pipeline: load → ask the evaluator (smart model, rubric + transcript + objective signals) → run the
 * post-processing chain ({@link EvaluationSteps}) → persist the evaluation, competency scores and topic statistics.
 * Idempotent: a retry recomputes everything for the session.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    static final String PROMPT_VERSION = "evaluation.v1";

    private final SessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;
    private final TurnRepository turns;
    private final EvaluationRepository evaluations;
    private final InterviewJdbcRepository jdbc;
    private final QuestionService questions;
    private final RubricService rubrics;
    private final AiGateway ai;
    private final SessionEventStream stream;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EvaluationService(SessionRepository sessions, SessionQuestionRepository sessionQuestions, TurnRepository turns,
            EvaluationRepository evaluations, InterviewJdbcRepository jdbc, QuestionService questions,
            RubricService rubrics, AiGateway ai, SessionEventStream stream, ObjectMapper json,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.turns = turns;
        this.evaluations = evaluations;
        this.jdbc = jdbc;
        this.questions = questions;
        this.rubrics = rubrics;
        this.ai = ai;
        this.stream = stream;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Snapshot of everything the evaluator needs, loaded in one transaction. */
    private record Inputs(InterviewSession session, List<SessionQuestion> questions, List<Turn> transcript,
            Map<UUID, CodeSubmissionSummary> code, Rubric rubric, RubricDefinition definition, UUID evaluationId) {
    }

    /** Runs the evaluation; returns true on success. Failures are recorded on the evaluation (retryable). */
    public boolean evaluate(UUID sessionId) {
        Inputs in = tx.execute(status -> load(sessionId));
        stream.publish(sessionId, "evaluation.progress", Map.of("status", "RUNNING", "step", "Scoring against the rubric"));
        try {
            StructuredResult<EvaluationOutput> result = ai.structured("evaluation", variables(in), EvaluationOutput.class,
                    new CallContext(in.session().getUserId(), sessionId, AiPurpose.EVALUATION, in.session().getSeed()));
            stream.publish(sessionId, "evaluation.progress", Map.of("status", "RUNNING", "step", "Verifying evidence"));
            EvaluationRun run = EvaluationSteps.run(new EvaluationRun(in.session().getMode(), result.value(),
                    in.definition(), signals(in), candidateTurns(in)));
            tx.executeWithoutResult(status -> persist(in, result, run));
            return true;
        } catch (AppException e) {
            log.warn("Evaluation of session {} failed: {}", sessionId, e.getMessage());
            tx.executeWithoutResult(status -> evaluations.findById(in.evaluationId()).ifPresent(ev -> {
                ev.fail(e.getMessage());
                evaluations.save(ev);
            }));
            return false;
        }
    }

    // ------------------------------------------------------------------ load

    private Inputs load(UUID sessionId) {
        InterviewSession session = sessions.findById(sessionId).orElseThrow();
        Rubric rubric = rubrics.require(session.getRubricId());
        Evaluation evaluation = evaluations.findBySessionId(sessionId)
                .orElseGet(() -> new Evaluation(sessionId, rubric.getId()));
        evaluation.start();
        evaluation = evaluations.save(evaluation);
        Map<UUID, CodeSubmissionSummary> code = new HashMap<>();
        jdbc.latestSubmissions(sessionId).forEach(s -> code.put(s.sessionQuestionId(), s));
        return new Inputs(session, sessionQuestions.findBySessionIdOrderByPosition(sessionId),
                turns.findBySessionIdOrderBySeq(sessionId), code, rubric, rubrics.definition(rubric), evaluation.getId());
    }

    /** Questions the candidate actually answered (unanswered ones are not scored). */
    private static List<SessionQuestion> answered(Inputs in) {
        Set<UUID> withAnswers = in.transcript().stream().filter(t -> t.getKind() == TurnKind.ANSWER)
                .map(Turn::getSessionQuestionId).collect(Collectors.toSet());
        return in.questions().stream().filter(q -> withAnswers.contains(q.getId())).toList();
    }

    private static List<QuestionSignal> signals(Inputs in) {
        return answered(in).stream().map(q -> {
            CodeSubmissionSummary c = in.code().get(q.getId());
            boolean executed = c != null && !"NOT_EXECUTED".equals(c.verdict());
            return new QuestionSignal(q.getPosition(), q.getHintsUsed(),
                    q.getTurnScore() == null ? null : q.getTurnScore().doubleValue(), executed ? c.passed() : 0,
                    executed ? c.total() : 0, executed);
        }).toList();
    }

    private static List<CandidateTurn> candidateTurns(Inputs in) {
        return in.transcript().stream().filter(t -> t.getRole() == TurnRole.CANDIDATE)
                .map(t -> new CandidateTurn(t.getSeq(), t.getContent())).toList();
    }

    // ------------------------------------------------------------------ prompt variables

    private Map<String, Object> variables(Inputs in) {
        InterviewSession session = in.session();
        Map<String, Object> vars = new HashMap<>();
        vars.put("mode", session.getMode().name());
        vars.put("modeLabel", modeLabel(session.getMode()));
        vars.put("roleLabel", session.getTargetRole() == TargetRole.SDE_2 ? "SDE-2 (mid-level)" : "SDE-1 (entry-level)");
        vars.put("rubricKey", in.rubric().getKey());
        vars.put("rubricVersion", in.rubric().getVersion());
        vars.put("rubricJson", write(in.rubric().getDefinition()));
        vars.put("outputSchema", EvaluationOutput.SCHEMA);

        List<SessionQuestion> answered = answered(in);
        List<Map<String, Object>> objective = new ArrayList<>();
        List<Map<String, Object>> questionList = new ArrayList<>();
        List<Map<String, Object>> mockQuestions = new ArrayList<>();
        for (SessionQuestion q : answered) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("position", q.getPosition());
            o.put("hintsUsed", q.getHintsUsed());
            o.put("followUps", q.getFollowUps());
            CodeSubmissionSummary c = in.code().get(q.getId());
            if (c != null) {
                o.put("code", "NOT_EXECUTED".equals(c.verdict())
                        ? Map.of("executed", false, "note", "code execution disabled; correctness unmeasured")
                        : Map.of("executed", true, "kind", c.kind(), "verdict", c.verdict(), "passed", c.passed(),
                                "total", c.total(), "language", c.language()));
            }
            objective.add(o);
            String reference = reference(q);
            questionList.add(Map.of("position", q.getPosition(), "title", q.getTitle(), "difficulty", q.getDifficulty(),
                    "tags", q.getTags(), "referenceNotes", reference));

            Map<String, Object> mq = new HashMap<>();
            mq.put("position", q.getPosition());
            mq.put("title", q.getTitle());
            mq.put("referenceMd", reference);
            mq.put("turnScore", q.getTurnScore() == null ? 5.0 : q.getTurnScore().doubleValue());
            mq.put("tags", q.getTags());
            JsonNode assessment = q.getAssessment();
            List<String> missing = new ArrayList<>();
            if (assessment != null) {
                assessment.path("missingPoints").forEach(n -> missing.add(n.asText()));
                if (assessment.hasNonNull("star")) {
                    mq.put("star", json.convertValue(assessment.get("star"), Map.class));
                }
            }
            mq.put("missingPoints", missing);
            mockQuestions.add(mq);
        }
        vars.put("objectiveSignalsJson", write(objective));
        vars.put("questionsJson", write(questionList));
        vars.put("transcript", transcript(in));

        // inputs for AI_PROVIDER=mock (ignored by real providers; see AiTypes.LlmRequest)
        vars.put("mockQuestions", mockQuestions);
        vars.put("mockCandidateTurns", candidateTurns(in).stream()
                .map(t -> Map.<String, Object>of("seq", t.seq(), "content", t.content())).toList());
        vars.put("mockCompetencies", in.definition().competencies().stream()
                .map(c -> Map.<String, Object>of("competency", c.competency().name(),
                        "criteria", c.criteria().stream().map(RubricDefinition.CriterionDef::id).toList()))
                .toList());
        List<String> integrity = new ArrayList<>();
        in.session().getIntegrity().forEach(n -> integrity.add(n.asText()));
        vars.put("mockIntegrity", integrity);
        return vars;
    }

    private String transcript(Inputs in) {
        StringBuilder sb = new StringBuilder();
        for (Turn t : in.transcript()) {
            if (t.getKind() == TurnKind.SYSTEM_NOTICE) {
                continue;
            }
            sb.append("[seq ").append(t.getSeq()).append(" | ").append(t.getRole()).append(" | ").append(t.getKind())
                    .append("]\n").append(t.getContent());
            JsonNode meta = t.getMetadata();
            if (meta.hasNonNull("code")) {
                sb.append("\n```").append(meta.path("language").asText("")).append('\n').append(meta.get("code").asText())
                        .append("\n```");
            }
            if (meta.hasNonNull("mermaid")) {
                sb.append("\n```mermaid\n").append(meta.get("mermaid").asText()).append("\n```");
            }
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    /** Reference notes for the evaluator and the model answer seed: from the bank, or the generated question's signals. */
    private String reference(SessionQuestion q) {
        if (q.getQuestionId() == null) {
            List<String> signals = new ArrayList<>();
            q.getContext().path("expectedSignals").forEach(n -> signals.add("- " + n.asText()));
            return signals.isEmpty() ? "" : "A strong answer covers:\n" + String.join("\n", signals);
        }
        Question bank = questions.require(q.getQuestionId());
        StringBuilder sb = new StringBuilder();
        switch (bank.getType()) {
            case DSA -> {
                var d = questions.dsa(bank);
                sb.append("Expected complexity: time ").append(d.expectedTimeComplexity()).append(", space ")
                        .append(d.expectedSpaceComplexity()).append("\n");
                d.expectedPoints().forEach(p -> sb.append("- ").append(p).append('\n'));
                if (d.referenceSolutionPython() != null && !d.referenceSolutionPython().isBlank()) {
                    sb.append("\nReference solution (Python):\n```python\n").append(d.referenceSolutionPython()).append("\n```");
                }
            }
            case LLD -> {
                var d = questions.lld(bank);
                sb.append(d.referenceDesignMd());
                if (d.referenceMermaid() != null && !d.referenceMermaid().isBlank()) {
                    sb.append("\n\n```mermaid\n").append(d.referenceMermaid()).append("\n```");
                }
            }
            case BEHAVIOURAL -> {
                var d = questions.behavioural(bank);
                sb.append("What good looks like: ").append(d.whatGoodLooksLike()).append('\n');
                d.expectedPoints().forEach(p -> sb.append("- ").append(p).append('\n'));
            }
        }
        return sb.toString().trim();
    }

    static String modeLabel(InterviewMode mode) {
        return switch (mode) {
            case DSA -> "coding (DSA)";
            case LLD -> "low-level design";
            case BEHAVIOURAL -> "behavioural";
            case RESUME_DEEP_DIVE -> "resume deep-dive";
        };
    }

    // ------------------------------------------------------------------ persist

    private void persist(Inputs in, StructuredResult<EvaluationOutput> result, EvaluationRun run) {
        InterviewSession session = in.session();
        EvaluationOutput out = result.value();
        Evaluation evaluation = evaluations.findById(in.evaluationId()).orElseThrow();
        Instant now = clock.instant();

        ArrayNode perQuestion = json.createArrayNode();
        Map<Integer, EvaluationOutput.QuestionResult> byPosition = new HashMap<>();
        out.perQuestion().forEach(r -> byPosition.putIfAbsent(r.position(), r));
        for (SessionQuestion q : in.questions()) {
            ObjectNode node = perQuestion.addObject();
            node.put("position", q.getPosition());
            node.put("sessionQuestionId", q.getId().toString());
            node.put("questionId", q.getQuestionId() == null ? null : q.getQuestionId().toString());
            node.put("title", q.getTitle());
            node.put("difficulty", q.getDifficulty().name());
            node.put("difficultyReason", q.getDifficultyReason());
            node.set("tags", json.valueToTree(q.getTags()));
            node.put("hintsUsed", q.getHintsUsed());
            node.put("followUps", q.getFollowUps());
            if (q.getTurnScore() != null) {
                node.put("turnScore", q.getTurnScore().doubleValue());
            }
            CodeSubmissionSummary c = in.code().get(q.getId());
            if (c != null) {
                node.set("code", json.valueToTree(Map.of("verdict", c.verdict(), "passed", c.passed(), "total", c.total(),
                        "language", c.language(), "executed", !"NOT_EXECUTED".equals(c.verdict()))));
            }
            EvaluationOutput.QuestionResult r = byPosition.get(q.getPosition());
            if (r != null) {
                node.put("modelAnswerMd", r.modelAnswerMd());
                node.set("keyMissedPoints", json.valueToTree(r.keyMissedPoints()));
                if (r.star() != null) {
                    node.set("star", json.valueToTree(r.star()));
                }
            } else {
                node.put("answered", false);
            }
        }

        ArrayNode integrity = json.createArrayNode();
        out.integrityFlags().forEach(f -> integrity.add(json.valueToTree(f)));
        in.session().getIntegrity().forEach(n -> integrity.addObject().put("type", "CLIENT_SIGNAL").put("detail", n.asText()));

        evaluation.succeed(BigDecimal.valueOf(run.overall()).setScale(1, RoundingMode.HALF_UP), run.confidence(),
                out.summary(), json.valueToTree(out.strengths()), json.valueToTree(out.gaps()),
                json.valueToTree(out.nextSteps()), perQuestion, integrity, ai.providerName(), result.raw().model(),
                PROMPT_VERSION, now);
        evaluations.save(evaluation);

        jdbc.deleteCompetencyScores(evaluation.getId());
        for (CompetencyScore cs : run.scores().values()) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("quotes", cs.evidence());
            evidence.put("adjustments", cs.adjustments());
            jdbc.insertCompetencyScore(evaluation.getId(), session.getUserId(), session.getMode().name(),
                    cs.competency().name(), cs.score(), cs.weight(), cs.lowConfidence(), cs.rationale(), write(evidence));
        }

        // topic statistics + spaced repetition: each answered question's turn score counts for each of its tags.
        // Same transaction as the evaluation, and only FAILED evaluations can be retried, so this runs once per session.
        {
            Map<String, List<Double>> byTag = new LinkedHashMap<>();
            for (SessionQuestion q : answered(in)) {
                double score = q.getTurnScore() == null ? 5.0 : q.getTurnScore().doubleValue();
                Set<String> tags = new LinkedHashSet<>(q.getTags());
                tags.forEach(tag -> byTag.computeIfAbsent(tag.toLowerCase(), k -> new ArrayList<>()).add(score));
            }
            byTag.forEach((tag, scores) -> {
                double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                jdbc.upsertTopic(session.getUserId(),
                        SpacedRepetition.next(tag, jdbc.topic(session.getUserId(), tag).orElse(null), avg, now));
            });
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
