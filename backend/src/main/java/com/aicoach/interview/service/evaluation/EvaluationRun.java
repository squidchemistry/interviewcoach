package com.aicoach.interview.service.evaluation;

import com.aicoach.ai.schema.EvaluationOutput;
import com.aicoach.ai.schema.EvaluationOutput.Competency;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.question.dto.RubricDefinition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Working state passed along the evaluation chain: inputs (model output, rubric, objective signals) and the scores
 * each step refines. Pure data — no I/O — so every step is unit-testable.
 */
public final class EvaluationRun {

    /** One answered question's objective facts. {@code testsTotal == 0} means code was not executed. */
    public record QuestionSignal(int position, int hintsUsed, Double turnScore, int testsPassed, int testsTotal,
            boolean executed) {
    }

    /** A candidate turn the evidence may quote. */
    public record CandidateTurn(int seq, String content) {
    }

    /** Computed competency score (0-10), with the verified evidence. */
    public static final class CompetencyScore {
        final Competency competency;
        final double weight;
        double score;
        boolean lowConfidence;
        final String rationale;
        final List<EvaluationOutput.Evidence> evidence = new ArrayList<>();
        final List<String> adjustments = new ArrayList<>();

        CompetencyScore(Competency competency, double weight, double score, String rationale) {
            this.competency = competency;
            this.weight = weight;
            this.score = score;
            this.rationale = rationale;
        }

        public Competency competency() {
            return competency;
        }

        public double weight() {
            return weight;
        }

        public double score() {
            return score;
        }

        public boolean lowConfidence() {
            return lowConfidence;
        }

        public String rationale() {
            return rationale;
        }

        public List<EvaluationOutput.Evidence> evidence() {
            return evidence;
        }

        public List<String> adjustments() {
            return adjustments;
        }
    }

    final InterviewMode mode;
    final EvaluationOutput output;
    final RubricDefinition rubric;
    final List<QuestionSignal> signals;
    final List<CandidateTurn> candidateTurns;
    final Map<Competency, CompetencyScore> scores = new EnumMap<>(Competency.class);
    int droppedEvidence;
    double overall;
    String confidence;

    public EvaluationRun(InterviewMode mode, EvaluationOutput output, RubricDefinition rubric, List<QuestionSignal> signals,
            List<CandidateTurn> candidateTurns) {
        this.mode = mode;
        this.output = output;
        this.rubric = rubric;
        this.signals = signals;
        this.candidateTurns = candidateTurns;
    }

    public Map<Competency, CompetencyScore> scores() {
        return scores;
    }

    /** Overall score 0-100. */
    public double overall() {
        return overall;
    }

    public String confidence() {
        return confidence;
    }

    public int droppedEvidence() {
        return droppedEvidence;
    }
}
