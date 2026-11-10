package com.aicoach.interview.service.round;

import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.schema.GeneratedQuestions;
import com.aicoach.ai.schema.GeneratedQuestions.GeneratedQuestion;
import com.aicoach.ai.schema.ParsedResume;
import com.aicoach.ai.schema.ParsedResume.Project;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.common.error.AppException;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.service.engine.SessionContext;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.service.QuestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Resume deep-dive: questions are generated from the candidate's own projects when the session is created. Each
 * generated question must name a project that really exists in the parsed resume (guards against hallucination);
 * invalid ones are dropped and replaced with curated generic project questions.
 */
@Component
public class ResumeDeepDiveRound extends BaseRound {

    private static final Logger log = LoggerFactory.getLogger(ResumeDeepDiveRound.class);

    private final List<JsonNode> fallback;

    public ResumeDeepDiveRound(AiGateway ai, QuestionService questions, QuestionSelector selector,
            DifficultyPolicy policy, ObjectMapper json) throws IOException {
        super(ai, questions, selector, policy, json);
        try (InputStream in = new ClassPathResource("seed/resume-fallback-questions.json").getInputStream()) {
            List<JsonNode> items = new ArrayList<>();
            json.readTree(in).forEach(items::add);
            this.fallback = List.copyOf(items);
        }
    }

    @Override
    public InterviewMode mode() {
        return InterviewMode.RESUME_DEEP_DIVE;
    }

    @Override
    protected QuestionType questionType() {
        return null;
    }

    @Override
    protected String modeLabel() {
        return "resume deep-dive";
    }

    /** Generates the session's questions from the resume (stored in the session's plan). */
    public ObjectNode preparePlan(ParsedResume resume, int count, CallContext context) {
        Set<String> projectNames = resume.projects().stream().map(p -> p.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<GeneratedQuestion> valid = new ArrayList<>();
        if (!resume.projects().isEmpty()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("roleLabel", "software engineer");
            vars.put("count", count);
            vars.put("projects", projectsText(resume.projects()));
            vars.put("projectList", resume.projects().stream().map(p -> Map.of("name", p.name(), "techStack", p.techStack()))
                    .toList());
            vars.put("outputSchema", GeneratedQuestions.SCHEMA);
            try {
                GeneratedQuestions generated = ai.structured("resume-question-generation", vars, GeneratedQuestions.class,
                        context).value();
                for (GeneratedQuestion q : generated.questions()) {
                    if (projectNames.contains(q.projectName().toLowerCase(Locale.ROOT)) && valid.size() < count) {
                        valid.add(q);
                    } else {
                        log.info("Dropped generated question about unknown project '{}'", q.projectName());
                    }
                }
            } catch (AppException e) {
                log.warn("Resume question generation failed ({}); using curated questions", e.getMessage());
            }
        }
        ObjectNode plan = json.createObjectNode();
        ArrayNode items = plan.putArray("questions");
        for (GeneratedQuestion q : valid) {
            Project project = resume.projects().stream().filter(p -> p.name().equalsIgnoreCase(q.projectName()))
                    .findFirst().orElse(null);
            ObjectNode item = items.addObject();
            item.put("title", "Project: " + q.projectName());
            item.put("question", q.question());
            item.put("angle", q.angle());
            item.put("projectName", q.projectName());
            item.set("expectedSignals", json.valueToTree(q.expectedSignals()));
            item.set("tags", json.valueToTree(project == null ? List.of() : project.techStack().stream()
                    .map(t -> t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""))
                    .filter(t -> !t.isBlank()).limit(4).toList()));
        }
        for (int i = 0; items.size() < count && i < fallback.size(); i++) {
            JsonNode q = fallback.get(i);
            ObjectNode item = items.addObject();
            item.put("title", "Your projects: " + q.path("angle").asText().toLowerCase(Locale.ROOT));
            item.put("question", q.path("question").asText());
            item.put("angle", q.path("angle").asText());
            item.set("expectedSignals", q.path("expectedSignals"));
            item.set("tags", json.valueToTree(List.of("projects")));
        }
        return plan;
    }

    @Override
    public PlannedQuestion pickQuestion(SessionContext ctx, Difficulty difficulty, int position) {
        JsonNode items = ctx.session().getPlan().path("questions");
        JsonNode item = items.get(Math.min(position, Math.max(0, items.size() - 1)));
        if (item == null) {
            throw new AppException(com.aicoach.common.error.ErrorCode.NO_QUESTIONS_AVAILABLE,
                    "No resume questions could be prepared");
        }
        List<String> tags = new ArrayList<>();
        item.path("tags").forEach(t -> tags.add(t.asText()));
        ObjectNode context = json.createObjectNode();
        context.set("expectedSignals", item.path("expectedSignals"));
        context.put("angle", item.path("angle").asText());
        if (item.hasNonNull("projectName")) {
            context.put("projectName", item.get("projectName").asText());
        }
        return new PlannedQuestion(null, item.path("title").asText(), item.path("question").asText(), difficulty, tags,
                context);
    }

    public int questionsAvailable(ParsedResume resume) {
        return resume.projects().size();
    }

    private static String projectsText(List<Project> projects) {
        StringBuilder sb = new StringBuilder();
        for (Project p : projects) {
            sb.append("- ").append(p.name()).append(": ").append(p.summary());
            if (!p.techStack().isEmpty()) {
                sb.append(" [").append(String.join(", ", p.techStack())).append("]");
            }
            if (!p.highlights().isEmpty()) {
                sb.append(" Highlights: ").append(String.join("; ", p.highlights()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static CallContext context(java.util.UUID userId, long seed) {
        return new CallContext(userId, null, AiPurpose.QUESTION_GENERATION, seed);
    }
}
