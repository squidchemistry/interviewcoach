package com.aicoach.question.web;

import com.aicoach.ai.AiUsageRow;
import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.common.web.PageResponse;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.dto.QuestionDtos.AdminQuestionResponse;
import com.aicoach.question.dto.QuestionDtos.CreateRubricRequest;
import com.aicoach.question.dto.QuestionDtos.ReindexResponse;
import com.aicoach.question.dto.QuestionDtos.RubricResponse;
import com.aicoach.question.dto.QuestionDtos.StatusRequest;
import com.aicoach.question.dto.QuestionDtos.UpsertQuestionRequest;
import com.aicoach.question.service.AiUsageService;
import com.aicoach.question.service.EmbeddingBackfillJob;
import com.aicoach.question.service.QuestionAdminService;
import com.aicoach.question.service.QuestionService.SearchFilter;
import com.aicoach.question.service.RubricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced for all of {@code /api/v1/admin/**}): question bank, rubrics, AI usage. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminContentController {

    private final QuestionAdminService questions;
    private final RubricService rubrics;
    private final EmbeddingBackfillJob embeddings;
    private final AiUsageService aiUsage;

    public AdminContentController(QuestionAdminService questions, RubricService rubrics, EmbeddingBackfillJob embeddings,
            AiUsageService aiUsage) {
        this.questions = questions;
        this.rubrics = rubrics;
        this.embeddings = embeddings;
        this.aiUsage = aiUsage;
    }

    @GetMapping("/questions")
    @Operation(summary = "Search all questions, including drafts and archived")
    public PageResponse<AdminQuestionResponse> search(
            @RequestParam(name = "type", required = false) QuestionType type,
            @RequestParam(name = "status", required = false) QuestionStatus status,
            @RequestParam(name = "difficulty", required = false) Difficulty difficulty,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return questions.search(new SearchFilter(type, difficulty, null, null, q), status, page, pageSize);
    }

    @GetMapping("/questions/{id}")
    public AdminQuestionResponse get(@PathVariable("id") UUID id) {
        return questions.get(id);
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a question (details validated per type; embedding computed in the background)")
    public AdminQuestionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpsertQuestionRequest body) {
        return questions.create(AuthenticatedUser.id(jwt), body);
    }

    @PutMapping("/questions/{id}")
    @Operation(summary = "Replace a question; send expectedVersion for optimistic concurrency")
    public AdminQuestionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody UpsertQuestionRequest body) {
        return questions.update(AuthenticatedUser.id(jwt), id, body);
    }

    @PostMapping("/questions/{id}/status")
    @Operation(summary = "Publish, unpublish (draft) or archive a question")
    public AdminQuestionResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody StatusRequest body) {
        return questions.changeStatus(AuthenticatedUser.id(jwt), id, body.status());
    }

    @PostMapping("/embeddings/reindex")
    @Operation(summary = "Embed every question missing an up-to-date embedding now")
    public ReindexResponse reindex() {
        return new ReindexResponse(embeddings.embedPending(), aiUsage.embeddingModel());
    }

    @GetMapping("/rubrics")
    public List<RubricResponse> rubrics() {
        return rubrics.list();
    }

    @PostMapping("/rubrics")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new (inactive) rubric version")
    public RubricResponse createRubric(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRubricRequest body) {
        return rubrics.create(AuthenticatedUser.id(jwt), body);
    }

    @PostMapping("/rubrics/{id}/activate")
    @Operation(summary = "Activate a rubric version (new sessions use it; old reports keep theirs)")
    public RubricResponse activateRubric(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return rubrics.activate(AuthenticatedUser.id(jwt), id);
    }

    @GetMapping("/ai-usage")
    @Operation(summary = "AI calls, tokens, cache hit rate, p95 latency and error rate")
    public List<AiUsageRow> aiUsage(
            @RequestParam(name = "groupBy", defaultValue = "day") @Pattern(regexp = "day|purpose|model") String groupBy,
            @RequestParam(name = "days", defaultValue = "14") @Min(1) @Max(90) int days) {
        Instant to = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return aiUsage.usage(groupBy, to.minusSeconds(days * 86_400L), to);
    }
}
