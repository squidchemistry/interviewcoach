package com.aicoach.interview.service.evaluation;

import com.aicoach.ai.schema.EvaluationOutput;
import com.aicoach.ai.schema.EvaluationOutput.Competency;
import com.aicoach.ai.schema.EvaluationOutput.CompetencyResult;
import com.aicoach.ai.schema.EvaluationOutput.CriterionScore;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.service.evaluation.EvaluationRun.CandidateTurn;
import com.aicoach.interview.service.evaluation.EvaluationRun.CompetencyScore;
import com.aicoach.interview.service.evaluation.EvaluationRun.QuestionSignal;
import com.aicoach.question.dto.RubricDefinition.CompetencyDef;
import com.aicoach.question.dto.RubricDefinition.CriterionDef;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chain of Responsibility: the post-processing applied to the evaluator's output, in order. Each step reads and refines
 * {@link EvaluationRun}; the model scores criteria, the code owns every total.
 */
public final class EvaluationSteps {

    /** Low-confidence competencies (no verifiable evidence) are capped here. */
    static final double LOW_CONFIDENCE_CAP = 4.0;
    /** DSA: weight of the objective test pass rate in the correctness-related competencies. */
    static final double OBJECTIVE_WEIGHT = 0.4;

    private EvaluationSteps() {
    }

    public interface Step {
        void apply(EvaluationRun run);
    }

    /** The full chain, in order. */
    public static List<Step> chain() {
        return List.of(new CriterionScoring(), new EvidenceVerification(), new ObjectiveBlend(), new HintPenalty(),
                new LowConfidenceCap(), new Aggregation());
    }

    public static EvaluationRun run(EvaluationRun run) {
        chain().forEach(step -> step.apply(run));
        return run;
    }

    /** 1. Competency score = mean of the rubric's criteria the model scored (unknown criterion ids are ignored). */
    static final class CriterionScoring implements Step {
        @Override
        public void apply(EvaluationRun run) {
            Map<Competency, CompetencyResult> results = new HashMap<>();
            run.output.competencies().forEach(c -> results.putIfAbsent(c.competency(), c));
            for (CompetencyDef def : run.rubric.competencies()) {
                CompetencyResult result = results.get(def.competency());
                if (result == null) {
                    continue; // not assessed: excluded from the weighted total and reported as missing
                }
                Set<String> ids = def.criteria().stream().map(CriterionDef::id).collect(Collectors.toSet());
                double score = result.criteria().stream().filter(c -> ids.contains(c.id()))
                        .mapToInt(CriterionScore::score).average()
                        .orElse(result.criteria().stream().mapToInt(CriterionScore::score).average().orElse(0));
                CompetencyScore cs = new CompetencyScore(def.competency(), def.weight(), score, result.rationale());
                cs.evidence.addAll(result.evidence());
                run.scores.put(def.competency(), cs);
            }
        }
    }

    /** 2. Evidence must be a verbatim quote of a candidate turn; anything else is dropped (anti-hallucination). */
    static final class EvidenceVerification implements Step {
        @Override
        public void apply(EvaluationRun run) {
            Map<Integer, String> bySeq = new HashMap<>();
            run.candidateTurns.forEach(t -> bySeq.put(t.seq(), normalise(t.content())));
            for (CompetencyScore cs : run.scores.values()) {
                List<EvaluationOutput.Evidence> verified = cs.evidence.stream().map(e -> verify(e, bySeq, run.candidateTurns))
                        .filter(java.util.Objects::nonNull).toList();
                run.droppedEvidence += cs.evidence.size() - verified.size();
                cs.evidence.clear();
                cs.evidence.addAll(verified);
            }
        }

        /** The quote at its claimed turn, or at the turn that really contains it (seq corrected), or null. */
        private static EvaluationOutput.Evidence verify(EvaluationOutput.Evidence e, Map<Integer, String> bySeq,
                List<CandidateTurn> turns) {
            String quote = normalise(e.quote());
            if (quote.length() < 8) {
                return null;
            }
            String claimed = bySeq.get(e.turnSeq());
            if (claimed != null && claimed.contains(quote)) {
                return e;
            }
            for (CandidateTurn turn : turns) {
                if (normalise(turn.content()).contains(quote)) {
                    return new EvaluationOutput.Evidence(e.quote(), turn.seq());
                }
            }
            return null;
        }
    }

    /** 3. DSA: correctness-related competencies blend in the test pass rate when code really ran. */
    static final class ObjectiveBlend implements Step {
        @Override
        public void apply(EvaluationRun run) {
            if (run.mode != InterviewMode.DSA) {
                return;
            }
            List<QuestionSignal> executed = run.signals.stream().filter(s -> s.executed() && s.testsTotal() > 0).toList();
            if (executed.isEmpty()) {
                return;
            }
            double passRate = executed.stream().mapToDouble(s -> (double) s.testsPassed() / s.testsTotal()).average()
                    .orElse(0);
            for (Competency competency : List.of(Competency.CODE_QUALITY, Competency.EDGE_CASES)) {
                CompetencyScore cs = run.scores.get(competency);
                if (cs != null) {
                    double before = cs.score;
                    cs.score = (1 - OBJECTIVE_WEIGHT) * cs.score + OBJECTIVE_WEIGHT * 10 * passRate;
                    cs.adjustments.add(String.format(Locale.ROOT, "Blended with test pass rate %.0f%% (%.1f → %.1f)",
                            passRate * 100, before, cs.score));
                }
            }
        }
    }

    /** 4. Hints reduce problem solving by the rubric's penalties (cumulative per level), averaged over questions. */
    static final class HintPenalty implements Step {
        @Override
        public void apply(EvaluationRun run) {
            CompetencyScore cs = run.scores.get(Competency.PROBLEM_SOLVING);
            if (cs == null || run.signals.isEmpty()) {
                return;
            }
            double total = 0;
            int hints = 0;
            for (QuestionSignal s : run.signals) {
                for (int level = 1; level <= s.hintsUsed(); level++) {
                    total += run.rubric.penalty(level);
                }
                hints += s.hintsUsed();
            }
            double penalty = total / run.signals.size();
            if (penalty > 0) {
                cs.score = Math.max(0, cs.score - penalty);
                cs.adjustments.add(String.format(Locale.ROOT, "−%.1f for %d hint%s used", penalty, hints, hints == 1 ? "" : "s"));
            }
        }
    }

    /** 5. A competency with no verified evidence is low-confidence and capped. */
    static final class LowConfidenceCap implements Step {
        @Override
        public void apply(EvaluationRun run) {
            for (CompetencyScore cs : run.scores.values()) {
                if (cs.evidence.isEmpty()) {
                    cs.lowConfidence = true;
                    if (cs.score > LOW_CONFIDENCE_CAP) {
                        cs.adjustments.add(String.format(Locale.ROOT, "Capped at %.0f: no verifiable evidence", LOW_CONFIDENCE_CAP));
                        cs.score = LOW_CONFIDENCE_CAP;
                    }
                }
                cs.score = Math.round(Math.max(0, Math.min(10, cs.score)) * 10) / 10.0;
            }
        }
    }

    /** 6. Overall (0-100) = weighted mean of assessed competencies × 10; confidence from evidence coverage. */
    static final class Aggregation implements Step {
        @Override
        public void apply(EvaluationRun run) {
            double weights = run.scores.values().stream().mapToDouble(CompetencyScore::weight).sum();
            double weighted = run.scores.values().stream().mapToDouble(cs -> cs.weight * cs.score).sum();
            run.overall = weights == 0 ? 0 : Math.round(weighted / weights * 100) / 10.0;
            long low = run.scores.values().stream().filter(CompetencyScore::lowConfidence).count();
            int expected = run.rubric.competencies().size();
            long answered = run.signals.size();
            if (run.scores.size() < expected || low * 2 > run.scores.size() || answered == 0) {
                run.confidence = "LOW";
            } else if (low > 0 || answered < 2) {
                run.confidence = "MEDIUM";
            } else {
                run.confidence = "HIGH";
            }
        }
    }

    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"');
        return s.replaceAll("[\\s]+", " ").replaceAll("^[\\s.…]+|[\\s.…]+$", "").trim();
    }
}
