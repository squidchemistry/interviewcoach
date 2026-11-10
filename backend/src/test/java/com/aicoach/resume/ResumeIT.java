package com.aicoach.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** Resume upload, background parsing (mock AI) and a resume deep-dive built from it. */
@IntegrationTest
class ResumeIT {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    static byte[] pdf(List<String> lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(13);
                content.newLineAtOffset(40, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static final List<String> RESUME = List.of(
            "Aarav Mehta - Software Engineer",
            "Skills: Java, Spring Boot, PostgreSQL, Redis, Docker, React, TypeScript, Kafka",
            "PROJECTS",
            "ShopStream - Real-time order tracking platform",
            "Built with Java, Spring Boot, Kafka and PostgreSQL; handled 2k orders per minute.",
            "Designed an idempotent event consumer and reduced duplicate notifications by 90%.",
            "QuickNotes - Collaborative notes app",
            "React, TypeScript and Redis pub/sub for live cursors; 1,200 weekly active users.",
            "EXPERIENCE",
            "Software Engineering Intern, Flipkart (2024-05 to 2024-08)",
            "Migrated a cron job to a Kafka consumer and cut processing lag from 20 minutes to 30 seconds.",
            "EDUCATION",
            "B.Tech Computer Science, IIT Delhi, 2025");

    private JsonNode call(RequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    @Test
    void uploadsParsesAndPowersAResumeDeepDive() throws Exception {
        Session s = new AuthTestClient(mvc).register(AuthTestClient.uniqueEmail());
        MockMultipartFile file = new MockMultipartFile("file", "Aarav Resume.pdf", "application/pdf", pdf(RESUME));
        JsonNode uploaded = call(multipart("/api/v1/resumes").file(file).header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        String id = uploaded.get("id").asText();
        assertThat(uploaded.get("status").asText()).isIn("PARSING", "PARSED");
        assertThat(uploaded.get("active").asBoolean()).isTrue();

        JsonNode resume = null;
        for (int i = 0; i < 200; i++) {
            resume = call(get("/api/v1/resumes/" + id).header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
            if (!"PARSING".equals(resume.get("status").asText())) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(resume.get("status").asText()).as(resume.toString()).isEqualTo("PARSED");
        assertThat(resume.get("parsed").get("skills").findValuesAsText("name")).contains("Java", "Kafka");
        assertThat(resume.has("rawText")).isFalse();

        JsonNode session = call(post("/api/v1/sessions").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"RESUME_DEEP_DIVE\",\"questionCount\":3}"), 201);
        assertThat(session.get("mode").asText()).isEqualTo("RESUME_DEEP_DIVE");

        call(delete("/api/v1/resumes/" + id).header(HttpHeaders.AUTHORIZATION, s.bearer()), 204);
        call(get("/api/v1/resumes/" + id).header(HttpHeaders.AUTHORIZATION, s.bearer()), 404);
    }

    @Test
    void rejectsNonPdfsAndImageOnlyPdfs() throws Exception {
        Session s = new AuthTestClient(mvc).register(AuthTestClient.uniqueEmail());
        MockMultipartFile text = new MockMultipartFile("file", "resume.pdf", "application/pdf", "hello".getBytes());
        call(multipart("/api/v1/resumes").file(text).header(HttpHeaders.AUTHORIZATION, s.bearer()), 415);

        MockMultipartFile tiny = new MockMultipartFile("file", "blank.pdf", "application/pdf", pdf(List.of("Hi")));
        String id = call(multipart("/api/v1/resumes").file(tiny).header(HttpHeaders.AUTHORIZATION, s.bearer()), 202)
                .get("id").asText();
        JsonNode resume = null;
        for (int i = 0; i < 200; i++) {
            resume = call(get("/api/v1/resumes/" + id).header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
            if (!"PARSING".equals(resume.get("status").asText())) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(resume.get("status").asText()).isEqualTo("FAILED");
        assertThat(resume.get("error").asText()).contains("text");
    }
}
