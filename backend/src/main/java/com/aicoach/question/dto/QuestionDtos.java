package com.aicoach.question.dto;

import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.dto.QuestionDetails.Param;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Request/response records of the question bank API. */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record QuestionSummaryResponse(UUID id, String slug, QuestionType type, String title, Difficulty difficulty,
            List<String> tags, List<String> companies) {
    }

    public record SampleTest(JsonNode input, JsonNode expected) {
    }

    /** What a candidate may see of a DSA problem: signature, sample tests and starter code (no hidden tests/hints). */
    public record DsaView(String functionName, List<Param> params, String returnType, List<SampleTest> samples,
            int hiddenTestCount, Map<String, String> starterCode) {
    }

    public record LldView(List<String> requirements, List<String> focusAreas) {
    }

    public record BehaviouralView(String competency, @Nullable String leadershipPrinciple) {
    }

    public record QuestionResponse(UUID id, String slug, QuestionType type, String title, String bodyMd,
            Difficulty difficulty, List<String> tags, List<String> companies,
            @Nullable DsaView dsa, @Nullable LldView lld, @Nullable BehaviouralView behavioural) {
    }

    public record SimilarQuestionResponse(QuestionSummaryResponse question, double similarity) {
    }

    public record PackResponse(String slug, String name, String description, QuestionType mode, @Nullable String company,
            boolean pro, int questionCount) {
    }

    public record AdminQuestionResponse(UUID id, String slug, QuestionType type, String title, String bodyMd,
            Difficulty difficulty, QuestionStatus status, List<String> tags, List<String> companies, JsonNode detail,
            String source, long version, Instant updatedAt) {
    }

    public record UpsertQuestionRequest(
            @NotNull QuestionType type,
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be kebab-case") @Size(max = 80) String slug,
            @NotBlank @Size(min = 3, max = 160) String title,
            @NotBlank @Size(max = 20_000) String bodyMd,
            @NotNull Difficulty difficulty,
            @NotNull QuestionStatus status,
            @NotNull @Size(max = 6) List<@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String> tags,
            @NotNull @Size(max = 5) List<@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String> companies,
            @NotNull JsonNode detail,
            @Nullable Long expectedVersion) {
    }

    public record StatusRequest(@NotNull QuestionStatus status) {
    }

    public record RubricResponse(UUID id, String key, int version, boolean active, JsonNode definition, String changelog,
            Instant createdAt) {
    }

    public record CreateRubricRequest(
            @NotBlank @Pattern(regexp = "DSA|LLD|BEHAVIOURAL|RESUME_DEEP_DIVE") String key,
            @NotNull JsonNode definition,
            @Size(max = 500) String changelog) {
    }

    public record ReindexResponse(int embedded, String model) {
    }
}
