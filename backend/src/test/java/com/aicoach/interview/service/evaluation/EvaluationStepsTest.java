package com.aicoach.interview.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicoach.ai.schema.EvaluationOutput;
import com.aicoach.ai.schema.EvaluationOutput.Competency;
import com.aicoach.ai.schema.EvaluationOutput.CompetencyResult;
import com.aicoach.ai.schema.EvaluationOutput.CriterionScore;
import com.aicoach.ai.schema.EvaluationOutput.Evidence;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.service.evaluation.EvaluationRun.CandidateTurn;
import com.aicoach.interview.service.evaluation.EvaluationRun.QuestionSignal;
import com.aicoach.question.dto.RubricDefinition;
import com.aicoach.question.dto.RubricDefinition.CompetencyDef;
import com.aicoach.question.dto.RubricDefinition.CriterionDef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationStepsTest {

    static final String ANSWER = "I used a hash map from value to index so the lookup is constant time.";

    static RubricDefinition rubric() {
        return new RubricDefinition(List.of(
                new CompetencyDef(Competency.PROBLEM_SOLVING, "Problem solving", 0.5,
                        List.of(new CriterionDef("approach", "d", Map.of()), new CriterionDef("complexity", "d", Map.of()))),
                new CompetencyDef(Competency.CODE_QUALITY, "Code quality", 0.3,
                        List.of(new CriterionDef("correctness", "d", Map.of()))),
                new CompetencyDef(Competency.COMMUNICATION, "Communication", 0.2,
                        List.of(new CriterionDef("clarity", "d", Map.of())))),
                Map.of("hintLevel1", 0.5, "hintLevel2", 1.0, "hintLevel3", 2.0));
    }

    static CompetencyResult result(Competency c, List<CriterionScore> criteria, List<Evidence> evidence) {
        return new CompetencyResult(c, criteria, "rationale", evidence);
    }

    static EvaluationOutput output(List<CompetencyResult> competencies) {
        return new EvaluationOutput("A".repeat(40), competencies, List.of("s1", "s2"), List.of("g1", "g2"), List.of(),
                List.of(), List.of());
    }

    static Evidence quote(String q, int seq) {
        return new Evidence(q, seq);
    }

    @Test
    void computesScoresFromCriteriaAndWeights() {
        EvaluationOutput out = output(List.of(
                result(Competency.PROBLEM_SOLVING, List.of(new CriterionScore("approach", 8, "j"),
                        new CriterionScore("complexity", 6, "j"), new CriterionScore("made_up", 0, "j")),
                        List.of(quote("hash map from value to index", 3))),
                result(Competency.CODE_QUALITY, List.of(new CriterionScore("correctness", 9, "j")),
                        List.of(quote("lookup is constant time", 3))),
                result(Competency.COMMUNICATION, List.of(new CriterionScore("clarity", 5, "j")),
                        List.of(quote("I used a hash map", 3)))));
        EvaluationRun run = EvaluationSteps.run(new EvaluationRun(InterviewMode.BEHAVIOURAL, out, rubric(),
                List.of(new QuestionSignal(0, 0, 7.0, 0, 0, false), new QuestionSignal(1, 0, 6.0, 0, 0, false)),
                List.of(new CandidateTurn(3, ANSWER))));

        assertThat(run.scores().get(Competency.PROBLEM_SOLVING).score()).isEqualTo(7.0); // unknown criterion ignored
        // 0.5*7 + 0.3*9 + 0.2*5 = 7.2 → 72
        assertThat(run.overall()).isEqualTo(72.0);
        assertThat(run.confidence()).isEqualTo("HIGH");
        assertThat(run.droppedEvidence()).isZero();
    }

    @Test
    void dropsFabricatedEvidenceAndCapsLowConfidenceCompetencies() {
        EvaluationOutput out = output(List.of(
                result(Competency.PROBLEM_SOLVING, List.of(new CriterionScore("approach", 9, "j"),
                        new CriterionScore("complexity", 9, "j")), List.of(quote("I proved it optimal with a lemma", 3))),
                result(Competency.CODE_QUALITY, List.of(new CriterionScore("correctness", 8, "j")),
                        // right quote, wrong seq: corrected rather than dropped
                        List.of(quote("hash map from value to index", 99))),
                result(Competency.COMMUNICATION, List.of(new CriterionScore("clarity", 8, "j")), List.of())));
        EvaluationRun run = EvaluationSteps.run(new EvaluationRun(InterviewMode.LLD, out, rubric(),
                List.of(new QuestionSignal(0, 0, 7.0, 0, 0, false)), List.of(new CandidateTurn(3, ANSWER))));

        var ps = run.scores().get(Competency.PROBLEM_SOLVING);
        assertThat(ps.lowConfidence()).isTrue();
        assertThat(ps.score()).isEqualTo(EvaluationSteps.LOW_CONFIDENCE_CAP);
        assertThat(ps.adjustments()).anyMatch(a -> a.contains("no verifiable evidence"));
        assertThat(run.scores().get(Competency.CODE_QUALITY).evidence()).extracting(Evidence::turnSeq).containsExactly(3);
        assertThat(run.droppedEvidence()).isEqualTo(1);
        assertThat(run.confidence()).isEqualTo("LOW"); // 2 of 3 low-confidence, one question
    }

    @Test
    void dsaBlendsTestPassRateAndPenalisesHints() {
        EvaluationOutput out = output(List.of(
                result(Competency.PROBLEM_SOLVING, List.of(new CriterionScore("approach", 8, "j"),
                        new CriterionScore("complexity", 8, "j")), List.of(quote("hash map from value to index", 3))),
                result(Competency.CODE_QUALITY, List.of(new CriterionScore("correctness", 10, "j")),
                        List.of(quote("constant time", 3)))));
        EvaluationRun run = EvaluationSteps.run(new EvaluationRun(InterviewMode.DSA, out, rubric(),
                List.of(new QuestionSignal(0, 2, 7.0, 5, 10, true)), List.of(new CandidateTurn(3, ANSWER))));

        // hints 1+2 → 0.5 + 1.0
        assertThat(run.scores().get(Competency.PROBLEM_SOLVING).score()).isEqualTo(6.5);
        // 0.6*10 + 0.4*5 = 8
        assertThat(run.scores().get(Competency.CODE_QUALITY).score()).isEqualTo(8.0);
        // communication missing → excluded from the weighted total and confidence LOW
        assertThat(run.scores()).doesNotContainKey(Competency.COMMUNICATION);
        assertThat(run.overall()).isEqualTo(Math.round((0.5 * 6.5 + 0.3 * 8.0) / 0.8 * 100) / 10.0);
        assertThat(run.confidence()).isEqualTo("LOW");
    }

    @Test
    void mockExecutionDoesNotBlendTestResults() {
        EvaluationOutput out = output(List.of(result(Competency.CODE_QUALITY,
                List.of(new CriterionScore("correctness", 9, "j")), List.of(quote("constant time", 3)))));
        EvaluationRun run = EvaluationSteps.run(new EvaluationRun(InterviewMode.DSA, out, rubric(),
                List.of(new QuestionSignal(0, 0, 7.0, 0, 0, false)), List.of(new CandidateTurn(3, ANSWER))));
        assertThat(run.scores().get(Competency.CODE_QUALITY).score()).isEqualTo(9.0);
    }

    @Test
    void normalisationIgnoresCaseSmartQuotesAndWhitespace() {
        assertThat(EvaluationSteps.normalise("  I’m   USING a Map… ")).isEqualTo("i'm using a map");
    }
}
