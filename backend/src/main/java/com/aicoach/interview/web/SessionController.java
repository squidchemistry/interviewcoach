package com.aicoach.interview.web;

import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.common.web.PageResponse;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.interview.dto.InterviewDtos.CodeRunRequest;
import com.aicoach.interview.dto.InterviewDtos.CodeRunResponse;
import com.aicoach.interview.dto.InterviewDtos.CommandResponse;
import com.aicoach.interview.dto.InterviewDtos.CreateSessionRequest;
import com.aicoach.interview.dto.InterviewDtos.HintRequest;
import com.aicoach.interview.dto.InterviewDtos.ReportResponse;
import com.aicoach.interview.dto.InterviewDtos.SessionDetailResponse;
import com.aicoach.interview.dto.InterviewDtos.SessionSummaryResponse;
import com.aicoach.interview.dto.InterviewDtos.ShareLinkResponse;
import com.aicoach.interview.dto.InterviewDtos.ShareRequest;
import com.aicoach.interview.dto.InterviewDtos.SubmitAnswerRequest;
import com.aicoach.interview.dto.InterviewDtos.TelemetryRequest;
import com.aicoach.interview.service.InterviewService;
import com.aicoach.interview.service.stream.SessionStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Interviews")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final InterviewService interviews;
    private final SessionStreamService streams;

    public SessionController(InterviewService interviews, SessionStreamService streams) {
        this.interviews = interviews;
        this.streams = streams;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an interview (checks the free-plan quota, Pro features and resume)")
    public SessionSummaryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSessionRequest request) {
        return interviews.create(AuthenticatedUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "My interviews, newest first")
    public PageResponse<SessionSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "status", required = false) SessionStatus status,
            @RequestParam(name = "mode", required = false) InterviewMode mode,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return interviews.list(AuthenticatedUser.id(jwt), status, mode, page, pageSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full state for the interview room: questions, transcript and the stream position")
    public SessionDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.detail(AuthenticatedUser.id(jwt), id);
    }

    @GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent events: tokens, turns, state changes; resumes after Last-Event-ID")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "after", required = false) String after) {
        return streams.open(AuthenticatedUser.id(jwt), id, lastEventId != null ? lastEventId : after);
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.start(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/answers")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit an answer (idempotent by clientMessageId)")
    public CommandResponse answer(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody SubmitAnswerRequest request) {
        return interviews.submitAnswer(AuthenticatedUser.id(jwt), id, request);
    }

    @PostMapping("/{id}/hints")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request the next hint (coding rounds; levels 1-3 in order)")
    public CommandResponse hint(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody HintRequest request) {
        return interviews.hint(AuthenticatedUser.id(jwt), id, request.level());
    }

    @PostMapping("/{id}/code/run")
    @Operation(summary = "Run code against the sample tests")
    public CodeRunResponse run(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody CodeRunRequest request) {
        return interviews.runCode(AuthenticatedUser.id(jwt), id, request, false);
    }

    @PostMapping("/{id}/code/submit")
    @Operation(summary = "Run code against all tests (hidden tests report verdicts only)")
    public CodeRunResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody CodeRunRequest request) {
        return interviews.runCode(AuthenticatedUser.id(jwt), id, request, true);
    }

    @PostMapping("/{id}/retry-turn")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandResponse retry(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.retryTurn(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/end")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "End early: evaluates what was answered (or abandons if nothing was)")
    public CommandResponse end(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.end(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/abandon")
    public CommandResponse abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.abandon(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/telemetry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Integrity signals (pastes, tab switches): shown as context in the report, never penalised")
    public void telemetry(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody TelemetryRequest request) {
        interviews.telemetry(AuthenticatedUser.id(jwt), id, request);
    }

    @GetMapping("/{id}/report")
    @Operation(summary = "Report; evaluation.status tells whether scoring is still running")
    public ReportResponse report(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return interviews.report(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/evaluation/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retryEvaluation(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        interviews.retryEvaluation(AuthenticatedUser.id(jwt), id);
    }

    @PostMapping("/{id}/share")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a public, anonymised report link (the URL is shown once)")
    public ShareLinkResponse share(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody ShareRequest request) {
        return interviews.share(AuthenticatedUser.id(jwt), id, request.expiresInDays());
    }

    @DeleteMapping("/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        interviews.revokeShares(AuthenticatedUser.id(jwt), id);
    }
}
