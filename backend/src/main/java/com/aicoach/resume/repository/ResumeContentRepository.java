package com.aicoach.resume.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The PDF bytes of a resume: written once on upload, read once by the parser (never mapped on the entity). */
@Repository
public class ResumeContentRepository {

    private final JdbcTemplate jdbc;

    public ResumeContentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID userId, String filename, byte[] content, String sha256, int pageCount) {
        return jdbc.queryForObject("""
                INSERT INTO resumes (user_id, original_filename, size_bytes, sha256, page_count, content, status)
                VALUES (?, ?, ?, ?, ?, ?, 'UPLOADED') RETURNING id
                """, UUID.class, userId, filename, content.length, sha256, pageCount, content);
    }

    public byte[] content(UUID id) {
        return jdbc.queryForObject("SELECT content FROM resumes WHERE id = ?", byte[].class, id);
    }
}
