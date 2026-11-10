package com.aicoach.ai.schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Structured profile extracted from a resume (also accepted from the user as corrections). */
public record ParsedResume(
        @Size(max = 600) String summary,
        @NotNull @Size(max = 60) List<@Valid Skill> skills,
        @NotNull @Size(max = 10) List<@Valid Project> projects,
        @NotNull @Size(max = 10) List<@Valid Experience> experience,
        @Size(max = 10) List<@Size(max = 200) String> integrityFlags) {

    public record Skill(@NotBlank @Size(max = 40) String name, @NotBlank @Size(max = 20) String category) {
    }

    public record Project(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 500) String summary,
            @NotNull @Size(max = 15) List<@Size(max = 40) String> techStack,
            @NotNull @Size(max = 6) List<@Size(max = 200) String> highlights) {
    }

    public record Experience(
            @NotBlank @Size(max = 80) String company,
            @NotBlank @Size(max = 80) String title,
            @Size(max = 10) String start,
            @Size(max = 10) String end,
            @NotNull @Size(max = 6) List<@Size(max = 200) String> highlights) {
    }

    public static final String SCHEMA = """
            {"summary": string (max 600) | "NOT_A_RESUME",
             "skills": [{"name": string (max 40), "category": "LANGUAGE"|"FRAMEWORK"|"DATABASE"|"CLOUD"|"TOOL"|"CONCEPT"}] (max 60),
             "projects": [{"name": string (max 80), "summary": string (max 500), "techStack": string[] (max 15), "highlights": string[] (max 6)}] (max 10),
             "experience": [{"company": string, "title": string, "start": "YYYY-MM"|null, "end": "YYYY-MM"|"present"|null, "highlights": string[] (max 6)}] (max 10),
             "integrityFlags": string[]}""";
}
