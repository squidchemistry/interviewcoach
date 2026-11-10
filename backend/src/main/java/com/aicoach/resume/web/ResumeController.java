package com.aicoach.resume.web;

import com.aicoach.ai.schema.ParsedResume;
import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.resume.dto.ResumeResponse;
import com.aicoach.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "Resumes")
@SecurityRequirement(name = "bearerAuth")
public class ResumeController {

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a PDF resume (≤ 5 MB, ≤ 10 pages); parsed in the background")
    public ResumeResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return resumes.upload(AuthenticatedUser.id(jwt), file);
    }

    @GetMapping
    @Operation(summary = "My resumes, newest first")
    public List<ResumeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return resumes.list(AuthenticatedUser.id(jwt));
    }

    @GetMapping("/{id}")
    public ResumeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return resumes.get(AuthenticatedUser.id(jwt), id);
    }

    @PatchMapping("/{id}/parsed")
    @Operation(summary = "Correct the extracted profile")
    public ResumeResponse correct(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody ParsedResume parsed) {
        return resumes.correct(AuthenticatedUser.id(jwt), id, parsed);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Use this resume for resume deep-dive interviews")
    public ResumeResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return resumes.activate(AuthenticatedUser.id(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hard-delete a resume (file and extracted text)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        resumes.delete(AuthenticatedUser.id(jwt), id);
    }
}
