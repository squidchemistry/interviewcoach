package com.aicoach.ai.schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Quick, per-answer assessment that drives follow-ups and adaptive difficulty (not the final evaluation). */
public record TurnAssessment(
        @NotNull @DecimalMin("0") @DecimalMax("10") Double score,
        @NotNull @Size(max = 8) List<@Size(max = 240) String> coveredPoints,
        @NotNull @Size(max = 8) List<@Size(max = 240) String> missingPoints,
        boolean followUpRecommended,
        @Size(max = 200) String followUpFocus,
        @Valid Star star,
        @NotNull @Size(max = 5) List<@Size(max = 200) String> integrityFlags) {

    public record Star(
            @DecimalMin("0") @DecimalMax("10") double situation,
            @DecimalMin("0") @DecimalMax("10") double task,
            @DecimalMin("0") @DecimalMax("10") double action,
            @DecimalMin("0") @DecimalMax("10") double result) {

        public String weakest() {
            double min = Math.min(Math.min(situation, task), Math.min(action, result));
            if (min == result) return "RESULT";
            if (min == action) return "ACTION";
            if (min == task) return "TASK";
            return "SITUATION";
        }
    }

    public static final String SCHEMA = """
            {"score": number 0-10, "coveredPoints": string[] (max 8), "missingPoints": string[] (max 8),
             "followUpRecommended": boolean, "followUpFocus": string|null (what to probe next, max 200 chars),
             "star": {"situation": 0-10, "task": 0-10, "action": 0-10, "result": 0-10} | null  (behavioural only),
             "integrityFlags": string[] (e.g. "PROMPT_INJECTION_ATTEMPT", "OFF_TOPIC", "EMPTY_OR_EVASIVE")}""";
}
