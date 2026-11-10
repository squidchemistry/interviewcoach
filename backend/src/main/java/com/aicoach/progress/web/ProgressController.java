package com.aicoach.progress.web;

import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.progress.dto.ProgressDtos.ProgressResponse;
import com.aicoach.progress.dto.ProgressDtos.RevisionResponse;
import com.aicoach.progress.dto.ProgressDtos.UsageResponse;
import com.aicoach.progress.service.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Progress")
@SecurityRequirement(name = "bearerAuth")
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/usage")
    @Operation(summary = "This month's interviews against the plan limit, and today's AI tokens")
    public UsageResponse usage(@AuthenticationPrincipal Jwt jwt) {
        return progress.usage(AuthenticatedUser.id(jwt));
    }

    @GetMapping("/progress")
    @Operation(summary = "Dashboard: totals, score history, competency trends and weak topics")
    public ProgressResponse progress(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "mode", required = false) InterviewMode mode) {
        return progress.progress(AuthenticatedUser.id(jwt), mode == null ? null : mode.name());
    }

    @GetMapping("/revision-queue")
    @Operation(summary = "Spaced-repetition queue of topics (SM-2), due first, with questions to practise")
    public RevisionResponse revision(@AuthenticationPrincipal Jwt jwt) {
        return progress.revisionQueue(AuthenticatedUser.id(jwt));
    }
}
