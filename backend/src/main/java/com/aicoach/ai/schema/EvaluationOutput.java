package com.aicoach.ai.schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The evaluator's answer. The model scores rubric CRITERIA with verbatim evidence; competency and overall scores are
 * computed in code from the rubric weights (the model never produces totals).
 */
public record EvaluationOutput(
        @NotBlank @Size(min = 20, max = 900) String summary,
        @NotEmpty @Size(max = 5) List<@Valid CompetencyResult> competencies,
        @NotNull @Size(min = 2, max = 5) List<@NotBlank @Size(max = 300) String> strengths,
        @NotNull @Size(min = 2, max = 5) List<@NotBlank @Size(max = 300) String> gaps,
        @NotEmpty List<@Valid QuestionResult> perQuestion,
        @NotNull @Size(min = 3, max = 3) List<@Valid NextStep> nextSteps,
        @NotNull @Size(max = 5) List<@Valid IntegrityFlag> integrityFlags) {

    public enum Competency { PROBLEM_SOLVING, CODE_QUALITY, COMMUNICATION, DESIGN, EDGE_CASES }

    public enum ResourceType { PRACTICE, READ, MOCK, REFLECT }

    public record CompetencyResult(
            @NotNull Competency competency,
            @NotEmpty @Size(max = 6) List<@Valid CriterionScore> criteria,
            @NotBlank @Size(max = 600) String rationale,
            @NotNull @Size(max = 3) List<@Valid Evidence> evidence) {
    }

    public record CriterionScore(@NotBlank String id, @Min(0) @Max(10) int score, @NotBlank @Size(max = 400) String justification) {
    }

    public record Evidence(@NotBlank @Size(max = 400) String quote, @Min(0) int turnSeq) {
    }

    public record QuestionResult(
            @Min(0) int position,
            @NotBlank @Size(max = 6000) String modelAnswerMd,
            @NotNull @Size(max = 6) List<@Size(max = 300) String> keyMissedPoints,
            @Valid StarBreakdown star) {
    }

    public record StarBreakdown(@NotNull @Valid StarPart situation, @NotNull @Valid StarPart task,
            @NotNull @Valid StarPart action, @NotNull @Valid StarPart result) {
    }

    public record StarPart(@Min(0) @Max(10) int score, @NotBlank @Size(max = 300) String justification,
            @Size(max = 400) String quote) {
    }

    public record NextStep(@NotBlank @Size(max = 260) String action, @NotBlank @Size(max = 260) String why,
            @NotNull ResourceType resourceType, @Size(max = 60) String relatedTag) {
    }

    public record IntegrityFlag(@NotBlank @Size(max = 60) String type, @Size(max = 300) String detail) {
    }

    public static final String SCHEMA = """
            {
              "summary": string (40-700 chars),
              "competencies": [{
                "competency": "PROBLEM_SOLVING"|"CODE_QUALITY"|"COMMUNICATION"|"DESIGN"|"EDGE_CASES",
                "criteria": [{"id": rubric criterion id, "score": integer 0-10, "justification": string}],
                "rationale": string,
                "evidence": [{"quote": verbatim text copied from a CANDIDATE turn (max 300 chars), "turnSeq": that turn's seq}]
              }],
              "strengths": string[] (2-5), "gaps": string[] (2-5),
              "perQuestion": [{"position": int, "modelAnswerMd": markdown, "keyMissedPoints": string[],
                               "star": null | {"situation"|"task"|"action"|"result": {"score": 0-10, "justification": string, "quote": string|null}}}],
              "nextSteps": exactly 3 × {"action": concrete one-week task, "why": linked gap, "resourceType": "PRACTICE"|"READ"|"MOCK"|"REFLECT", "relatedTag": string|null},
              "integrityFlags": [{"type": "PROMPT_INJECTION_ATTEMPT"|"OFF_TOPIC"|"COPIED_QUESTION"|"EMPTY_OR_EVASIVE", "detail": string}]
            }""";
}
