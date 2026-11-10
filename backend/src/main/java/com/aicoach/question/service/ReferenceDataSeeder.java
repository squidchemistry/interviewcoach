package com.aicoach.question.service;

import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.domain.QuestionPack;
import com.aicoach.question.domain.Rubric;
import com.aicoach.question.repository.QuestionPackRepository;
import com.aicoach.question.repository.QuestionRepository;
import com.aicoach.question.repository.RubricRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the curated question bank, company packs and v1 rubrics from {@code classpath:seed/} on every startup.
 * Idempotent: existing questions (matched by slug) are never overwritten, so admin edits survive restarts.
 */
@Component
@Order(1)
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);
    private static final List<String> QUESTION_FILES = List.of("seed/questions/dsa.json", "seed/questions/lld.json",
            "seed/questions/behavioural.json");

    private final QuestionRepository questions;
    private final QuestionPackRepository packs;
    private final RubricRepository rubrics;
    private final QuestionAdminService admin;
    private final RubricService rubricService;
    private final ObjectMapper json;

    public ReferenceDataSeeder(QuestionRepository questions, QuestionPackRepository packs, RubricRepository rubrics,
            QuestionAdminService admin, RubricService rubricService, ObjectMapper json) {
        this.questions = questions;
        this.packs = packs;
        this.rubrics = rubrics;
        this.admin = admin;
        this.rubricService = rubricService;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        int added = 0;
        for (String file : QUESTION_FILES) {
            for (JsonNode node : read(file)) {
                String slug = node.get("slug").asText();
                if (questions.existsBySlug(slug)) {
                    continue;
                }
                QuestionType type = QuestionType.valueOf(node.get("type").asText());
                JsonNode detail = node.get(switch (type) {
                    case DSA -> "dsa";
                    case LLD -> "lld";
                    case BEHAVIOURAL -> "behavioural";
                });
                Question question = Question.create(type, slug, "SEED", null);
                question.update(node.get("title").asText(), node.get("bodyMd").asText(),
                        Difficulty.valueOf(node.get("difficulty").asText()), QuestionStatus.PUBLISHED,
                        strings(node.get("tags")), strings(node.get("companies")), admin.validateDetail(type, detail));
                questions.save(question);
                added++;
            }
        }
        for (JsonNode node : read("seed/packs.json")) {
            String slug = node.get("slug").asText();
            if (packs.findBySlug(slug).isEmpty()) {
                packs.save(new QuestionPack(slug, node.get("name").asText(), node.get("description").asText(),
                        QuestionType.valueOf(node.get("mode").asText()),
                        node.hasNonNull("company") ? node.get("company").asText() : null, node.path("pro").asBoolean(),
                        strings(node.get("questions"))));
            }
        }
        for (JsonNode node : read("seed/rubrics.json")) {
            String key = node.get("key").asText();
            if (rubrics.maxVersion(key) == 0) {
                Rubric rubric = new Rubric(key, 1, rubricService.validate(node.get("definition")),
                        node.path("changelog").asText(""), null);
                rubric.activate();
                rubrics.save(rubric);
            }
        }
        if (added > 0) {
            log.info("Seeded {} questions", added);
        }
    }

    private JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readTree(in);
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
