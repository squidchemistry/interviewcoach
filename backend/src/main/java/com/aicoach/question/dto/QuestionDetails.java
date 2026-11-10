package com.aicoach.question.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Type-specific question details, validated before they are stored as JSON. */
public final class QuestionDetails {

    private QuestionDetails() {
    }

    public static final String TYPES = "int|long|double|boolean|string|int\\[]|long\\[]|string\\[]|boolean\\[]|int\\[]\\[]|string\\[]\\[]";

    public record Param(
            @NotBlank @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$") String name,
            @NotBlank @Pattern(regexp = TYPES, message = "unsupported type") String type) {
    }

    public record TestCase(@NotNull JsonNode input, @NotNull JsonNode expected, boolean sample) {
    }

    public record DsaDetail(
            @NotBlank @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_]*$") String functionName,
            @NotEmpty @Size(max = 6) List<@Valid Param> params,
            @NotBlank @Pattern(regexp = TYPES, message = "unsupported type") String returnType,
            @NotBlank @Pattern(regexp = "EXACT|UNORDERED_LIST|FLOAT_TOLERANCE") String comparator,
            @NotBlank String expectedTimeComplexity,
            @NotBlank String expectedSpaceComplexity,
            @NotEmpty @Size(max = 25) List<@Valid TestCase> testCases,
            @NotNull @Size(min = 3, max = 3) List<@NotBlank String> hints,
            @NotNull List<String> expectedPoints,
            String referenceSolutionPython) {

        public List<TestCase> samples() {
            return testCases.stream().filter(TestCase::sample).toList();
        }
    }

    public record LldDetail(
            @NotEmpty List<@NotBlank String> requirements,
            @NotEmpty List<@NotBlank String> focusAreas,
            @NotBlank String referenceDesignMd,
            String referenceMermaid,
            @NotNull List<String> expectedPoints) {
    }

    public record BehaviouralDetail(
            @NotBlank String competency,
            String leadershipPrinciple,
            @NotBlank String whatGoodLooksLike,
            @NotNull List<String> followUpProbes,
            @NotNull List<String> expectedPoints) {
    }
}
