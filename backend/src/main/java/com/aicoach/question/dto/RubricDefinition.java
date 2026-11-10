package com.aicoach.question.dto;

import com.aicoach.ai.schema.EvaluationOutput.Competency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/** Typed view of a rubric's JSON definition. Weights of all competencies must sum to 1. */
public record RubricDefinition(
        @NotEmpty List<@Valid CompetencyDef> competencies,
        Map<String, Double> penalties) {

    public record CompetencyDef(
            @NotNull Competency competency,
            @NotBlank String label,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double weight,
            @NotEmpty List<@Valid CriterionDef> criteria) {
    }

    public record CriterionDef(
            @NotBlank @Pattern(regexp = "^[a-z0-9_]{2,40}$") String id,
            @NotBlank String description,
            @NotNull Map<String, String> levels) {
    }

    public double totalWeight() {
        return competencies.stream().mapToDouble(CompetencyDef::weight).sum();
    }

    public double penalty(int hintLevel) {
        if (penalties == null) {
            return 0;
        }
        return penalties.getOrDefault("hintLevel" + hintLevel, 0.0);
    }
}
