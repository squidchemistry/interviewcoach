package com.aicoach.question.web;

import com.aicoach.common.web.PageResponse;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.dto.QuestionDtos.PackResponse;
import com.aicoach.question.dto.QuestionDtos.QuestionResponse;
import com.aicoach.question.dto.QuestionDtos.QuestionSummaryResponse;
import com.aicoach.question.dto.QuestionDtos.SimilarQuestionResponse;
import com.aicoach.question.service.QuestionService;
import com.aicoach.question.service.QuestionService.SearchFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue")
@SecurityRequirement(name = "bearerAuth")
public class QuestionController {

    private final QuestionService questions;

    public QuestionController(QuestionService questions) {
        this.questions = questions;
    }

    @GetMapping("/questions")
    @Operation(summary = "Browse published questions (no hidden tests, hints or reference answers)")
    public PageResponse<QuestionSummaryResponse> search(
            @RequestParam(name = "type", required = false) QuestionType type,
            @RequestParam(name = "difficulty", required = false) Difficulty difficulty,
            @RequestParam(name = "tag", required = false) @Size(max = 40) String tag,
            @RequestParam(name = "company", required = false) @Size(max = 40) String company,
            @RequestParam(name = "q", required = false) @Size(max = 100) String q,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return questions.search(new SearchFilter(type, difficulty, tag, company, q), page, pageSize);
    }

    @GetMapping("/questions/{idOrSlug}")
    @Operation(summary = "One published question with its candidate-visible details and starter code")
    public QuestionResponse get(@PathVariable("idOrSlug") String idOrSlug) {
        return questions.getPublished(idOrSlug);
    }

    @GetMapping("/questions/{idOrSlug}/similar")
    @Operation(summary = "Nearest questions by embedding similarity (pgvector, cosine)")
    public List<SimilarQuestionResponse> similar(@PathVariable("idOrSlug") String idOrSlug,
            @RequestParam(name = "k", defaultValue = "5") @Min(1) @Max(10) int k) {
        return questions.similar(idOrSlug, k);
    }

    @GetMapping("/tags")
    @Operation(summary = "All tags used by published questions")
    public List<String> tags() {
        return questions.tags();
    }

    @GetMapping("/packs")
    @Operation(summary = "Company-specific question packs")
    public List<PackResponse> packs() {
        return questions.packs();
    }
}
