package com.aicoach.interview.web;

import com.aicoach.interview.dto.InterviewDtos.PublicReportResponse;
import com.aicoach.interview.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public")
public class PublicReportController {

    private final InterviewService interviews;

    public PublicReportController(InterviewService interviews) {
        this.interviews = interviews;
    }

    @GetMapping("/reports/{token}")
    @Operation(summary = "Anonymised shared report (404 for unknown, expired and revoked links alike)")
    public ResponseEntity<PublicReportResponse> report(@PathVariable("token") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Robots-Tag", "noindex").body(interviews.publicReport(token));
    }
}
