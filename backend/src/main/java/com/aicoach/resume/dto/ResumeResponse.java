package com.aicoach.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** A resume as the owner sees it (never the PDF bytes or the extracted raw text). */
public record ResumeResponse(UUID id, String filename, int sizeBytes, int pageCount, String status, boolean active,
        JsonNode parsed, JsonNode integrity, String error, Instant createdAt, Instant updatedAt) {
}
