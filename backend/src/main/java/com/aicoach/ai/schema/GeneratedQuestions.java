package com.aicoach.ai.schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Resume deep-dive questions generated from the candidate's own projects. */
public record GeneratedQuestions(@NotEmpty @Size(max = 8) List<@Valid GeneratedQuestion> questions) {

    public record GeneratedQuestion(
            @NotBlank @Size(max = 80) String projectName,
            @NotBlank @Size(max = 20) String angle,
            @NotBlank @Size(max = 400) String question,
            @NotNull @Size(min = 1, max = 5) List<@Size(max = 160) String> expectedSignals) {
    }

    public static final String SCHEMA = """
            {"questions": [{"projectName": exactly as given, "angle": "TRADEOFFS"|"SCALING"|"FAILURES"|"OWNERSHIP"|"TESTING",
                            "question": string (max 300), "expectedSignals": string[] (2-5)}]}""";
}
