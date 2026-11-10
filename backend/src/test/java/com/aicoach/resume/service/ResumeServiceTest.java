package com.aicoach.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.ai.schema.ParsedResume;
import com.aicoach.common.error.AppException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ResumeServiceTest {

    @Test
    void groundingDropsSkillsAndProjectsNotInTheText() {
        String text = "Built ShopStream with Java and Kafka. Familiar with C++ and Go.".toLowerCase();
        ParsedResume parsed = new ParsedResume("summary",
                List.of(new ParsedResume.Skill("Java", "LANGUAGE"), new ParsedResume.Skill("Rust", "LANGUAGE"),
                        new ParsedResume.Skill("C++", "LANGUAGE"), new ParsedResume.Skill("Go", "LANGUAGE"),
                        new ParsedResume.Skill("JavaScript", "LANGUAGE")),
                List.of(new ParsedResume.Project("ShopStream", "orders", List.of("Java", "Kubernetes"), List.of()),
                        new ParsedResume.Project("Imaginary Engine", "x", List.of(), List.of())),
                List.of(), List.of("flag from the model"));
        List<String> flags = new ArrayList<>();
        ParsedResume grounded = ResumeService.ground(parsed, text, flags);

        assertThat(grounded.skills()).extracting(ParsedResume.Skill::name).containsExactly("Java", "C++", "Go");
        assertThat(grounded.projects()).extracting(ParsedResume.Project::name).containsExactly("ShopStream");
        assertThat(grounded.projects().getFirst().techStack()).containsExactly("Java");
        assertThat(flags).hasSize(3).anyMatch(f -> f.contains("Imaginary Engine"));
        assertThat(grounded.integrityFlags()).isEmpty();
    }

    @Test
    void validatesUploadsAtTheBoundary() {
        assertThat(ResumeService.isPdf("%PDF-1.7".getBytes())).isTrue();
        assertThat(ResumeService.isPdf("PK\u0003\u0004".getBytes())).isFalse();
        assertThatThrownBy(() -> ResumeService.readValidated(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> ResumeService.readValidated(new MockMultipartFile("file", "x.pdf", "application/pdf",
                "<html>".getBytes()))).hasMessageContaining("PDF");
        assertThatThrownBy(() -> ResumeService.pageCount("%PDF-garbage".getBytes())).hasMessageContaining("readable");
    }

    @Test
    void sanitisesFilenames() {
        assertThat(ResumeService.sanitiseFilename("C:\\Users\\me\\My <CV>.pdf")).isEqualTo("My _CV_.pdf");
        assertThat(ResumeService.sanitiseFilename(null)).isEqualTo("resume.pdf");
        assertThat(ResumeService.sanitiseFilename("a".repeat(300))).hasSize(120);
    }
}
