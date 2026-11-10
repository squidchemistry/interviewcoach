package com.aicoach.ai.service.mock;

import com.aicoach.ai.AiTypes.LlmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-prompt generators for the mock provider. Each reads the prompt variables (the same ones the real templates
 * render) and builds a plausible answer. Heuristics are intentionally simple and transparent: coverage of the
 * question's expected points, answer depth, STAR markers and objective test results.
 */
final class MockScenarios {

    private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "that", "this", "from", "into",
            "your", "you", "are", "was", "were", "have", "has", "had", "how", "what", "why", "when", "then", "than",
            "each", "about", "they", "them", "their", "would", "could", "should", "which", "while", "using", "use",
            "used", "also", "only", "both", "more", "most", "some", "such", "very", "will", "can", "not", "all", "any",
            "its", "it's", "one", "two", "does", "make", "makes", "explain", "explains", "candidate", "strong", "answer");

    private static final List<String> SKILLS = List.of("Java", "Python", "JavaScript", "TypeScript", "C++", "Go", "Kotlin",
            "SQL", "Spring Boot", "Spring", "React", "Node.js", "Express", "Django", "Flask", "FastAPI", "Next.js",
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "Kafka", "RabbitMQ", "Elasticsearch", "Docker", "Kubernetes", "AWS",
            "GCP", "Azure", "Terraform", "Git", "Linux", "GraphQL", "REST", "gRPC", "Microservices", "Hibernate",
            "TensorFlow", "PyTorch", "Pandas", "Tailwind", "Jenkins", "GitHub Actions", "JUnit", "Selenium");

    private final ObjectMapper json;

    MockScenarios(ObjectMapper json) {
        this.json = json;
    }

    String respond(LlmRequest request) {
        Map<String, Object> v = request.variables();
        Random random = new Random(request.context().seed() * 31 + request.promptId().hashCode()
                + str(v, "intent").hashCode() + intValue(v, "followUps", 0) * 7L);
        return switch (request.promptId()) {
            case "interviewer" -> interviewer(v, random);
            case "hint" -> hint(v);
            case "turn-assessment" -> write(turnAssessment(v));
            case "evaluation" -> write(evaluation(v, random));
            case "resume-extraction" -> write(resumeExtraction(v));
            case "resume-question-generation" -> write(resumeQuestions(v));
            case "json-repair" -> str(v, "invalidOutput");
            default -> throw new IllegalArgumentException("Mock provider has no scenario for prompt " + request.promptId());
        };
    }

    // ------------------------------------------------------------------ interviewer

    private String interviewer(Map<String, Object> v, Random random) {
        String mode = str(v, "mode");
        String intent = str(v, "intent");
        if ("WRAP_UP".equals(intent)) {
            return "Thanks — that's all the questions I have for today. Give me a moment while I put your feedback together.";
        }
        if ("ASK".equals(intent)) {
            int position = intValue(v, "position", 1);
            StringBuilder text = new StringBuilder();
            if (position == 1) {
                String name = str(v, "candidateName");
                text.append("Hi").append(name.isBlank() ? "" : " " + name.split(" ")[0])
                        .append(", I'm Alex and I'll be your interviewer today. ")
                        .append(intro(mode)).append("\n\n");
            } else {
                text.append(pick(random, List.of("Thanks, that's helpful. Let's move on.", "Okay, good. Next question.",
                        "Great, let's switch gears."))).append("\n\n");
            }
            text.append("**Question ").append(position).append(" of ").append(intValue(v, "planned", 1)).append(": ")
                    .append(str(v, "questionTitle")).append("**\n\n").append(str(v, "questionMd").trim());
            String ask = switch (mode) {
                case "DSA" -> "\n\nBefore you write any code, talk me through your approach and its complexity.";
                case "LLD" -> "\n\nStart by listing the core entities and their responsibilities. A Mermaid class diagram is welcome but optional.";
                default -> "";
            };
            return text.append(ask).toString();
        }
        // FOLLOW_UP
        String focus = str(v, "focus").toLowerCase(Locale.ROOT);
        String answer = str(v, "lastAnswer");
        String opener = pick(random, List.of("Okay.", "Got it.", "Interesting.", "Thanks."));
        String quote = quotable(answer, random);
        String question = switch (mode) {
            case "BEHAVIOURAL" -> {
                if (focus.contains("result") || focus.contains("measur") || focus.contains("impact")) {
                    yield "How did you measure the outcome? What changed afterwards — ideally in numbers?";
                }
                if (focus.contains("action") || focus.contains("you personally") || focus.contains("ownership")) {
                    yield quote.isEmpty() ? "What exactly did *you* do, as opposed to the team?"
                            : "You said \"" + quote + "\". What exactly did *you* do there, as opposed to the team?";
                }
                if (focus.contains("situation") || focus.contains("context")) {
                    yield "Can you set the scene a bit more — which team, what was at stake, and what was the deadline?";
                }
                yield pick(random, List.of("Looking back, what would you do differently?",
                        "What did you learn from that, and have you applied it since?"));
            }
            case "DSA" -> {
                if (focus.contains("complex")) {
                    yield "What are the time and space complexities of that approach, and why?";
                }
                if (focus.contains("edge")) {
                    yield "Which edge cases would break a naive implementation here? How does yours handle them?";
                }
                if (focus.contains("optim") || focus.contains("better") || focus.contains("brute")) {
                    yield "Can you do better than that? Where exactly is the bottleneck?";
                }
                if (focus.contains("test") || focus.contains("fail")) {
                    yield "Some tests are failing. What category of input do you think your code mishandles?";
                }
                yield "Walk me through your approach on a small example, step by step.";
            }
            case "LLD" -> {
                if (focus.contains("concurren")) {
                    yield "Two requests try to take the same resource at the same moment. Walk me through what happens in your design.";
                }
                if (focus.contains("extens")) {
                    yield "A new requirement arrives that adds a new type of entity. Which classes change, and which stay closed?";
                }
                if (focus.contains("pattern")) {
                    yield "Which design pattern fits the main variation point in your design, and why that one?";
                }
                if (focus.contains("solid")) {
                    yield "Which of your classes has more than one reason to change right now? How would you split it?";
                }
                if (focus.contains("persist") || focus.contains("api")) {
                    yield "How would this state survive a restart, and what would the public API of the main service look like?";
                }
                yield "Which part of this design are you least confident about, and why?";
            }
            default -> {
                if (focus.contains("scal")) {
                    yield "If traffic grew 10×, what would break first, and what would you change?";
                }
                if (focus.contains("fail") || focus.contains("debug")) {
                    yield "Tell me about something that went wrong in production on this project. How did you find the cause?";
                }
                yield pick(random, List.of("Why did you pick that stack over the alternatives you considered?",
                        "Which part of that did you personally own, end to end?",
                        "How did you test it, and what did your tests miss?"));
            }
        };
        return opener + " " + question;
    }

    private static String intro(String mode) {
        return switch (mode) {
            case "DSA" -> "This is a coding round: think aloud, and use the editor to run your code against the sample tests.";
            case "LLD" -> "This is a low-level design round. I care more about your reasoning than about a perfect diagram.";
            case "BEHAVIOURAL" -> "This is a behavioural round. Specific stories with your own actions and measurable results work best.";
            default -> "I'd like to go deep into the projects on your resume.";
        };
    }

    // ------------------------------------------------------------------ hint

    private static String hint(Map<String, Object> v) {
        String seed = str(v, "seedHint");
        int level = intValue(v, "level", 1);
        String label = switch (level) {
            case 1 -> "Nudge";
            case 2 -> "Approach";
            default -> "Outline";
        };
        return "**Hint " + level + " · " + label + "** — " + (seed.isBlank() ? "Think about which data structure gives you fast lookups." : seed);
    }

    // ------------------------------------------------------------------ assessment

    Map<String, Object> turnAssessment(Map<String, Object> v) {
        String mode = str(v, "mode");
        String answers = str(v, "answers");
        List<String> expected = list(v, "expectedPoints");
        Set<String> answerWords = words(answers);
        int wordCount = answers.isBlank() ? 0 : answers.trim().split("\\s+").length;

        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String point : expected) {
            Set<String> keywords = words(point);
            long hits = keywords.stream().filter(answerWords::contains).count();
            if (!keywords.isEmpty() && hits >= Math.max(1, Math.round(keywords.size() * 0.34))) {
                covered.add(point);
            } else {
                missing.add(point);
            }
        }
        double coverage = expected.isEmpty() ? Math.min(1.0, wordCount / 150.0) : (double) covered.size() / expected.size();
        double depth = Math.min(1.0, wordCount / 140.0);
        double score = 1.5 + 5.0 * coverage + 3.0 * depth;

        Map<String, Object> star = null;
        if ("BEHAVIOURAL".equals(mode)) {
            String lower = answers.toLowerCase(Locale.ROOT);
            double situation = markers(lower, "when ", "at my", "project", "team", "company", "situation", "we were", "last year");
            double task = markers(lower, "responsible", "my task", "goal", "needed to", "had to", "my role", "deadline", "assigned");
            double action = markers(lower, "i decided", "i built", "i wrote", "i led", "i proposed", "i set up", "i talked",
                    "i created", "i changed", "i suggested", "i organised", "i organized", "i analysed", "i analyzed");
            double result = markers(lower, "result", "%", "reduced", "improved", "increased", "saved", "learned", "learnt",
                    "outcome", "shipped", "launched") + (lower.matches("(?s).*\\d+.*") ? 2 : 0);
            star = new LinkedHashMap<>();
            star.put("situation", clamp(situation));
            star.put("task", clamp(task));
            star.put("action", clamp(action));
            star.put("result", clamp(result));
            double starAvg = (clamp(situation) + clamp(task) + clamp(action) + clamp(result)) / 4.0;
            score = 0.5 * score + 0.5 * starAvg;
        }
        Integer passed = intOrNull(v, "testsPassed");
        Integer total = intOrNull(v, "testsTotal");
        if ("DSA".equals(mode) && passed != null && total != null && total > 0) {
            score = 0.6 * score + 0.4 * (10.0 * passed / total);
        }
        score = Math.round(Math.max(0, Math.min(10, score)) * 10) / 10.0;

        int followUps = intValue(v, "followUps", 0);
        String focus = focusFor(mode, star, missing, passed, total, followUps);
        List<String> flags = new ArrayList<>();
        if (wordCount < 8) {
            flags.add("EMPTY_OR_EVASIVE");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("coveredPoints", truncate(covered, 8));
        out.put("missingPoints", truncate(missing, 8));
        out.put("followUpRecommended", score < 8.5 && followUps < 2);
        out.put("followUpFocus", focus);
        out.put("star", star);
        out.put("integrityFlags", flags);
        return out;
    }

    private static String focusFor(String mode, Map<String, Object> star, List<String> missing, Integer passed,
            Integer total, int followUps) {
        if (star != null) {
            String weakest = "result";
            double min = Double.MAX_VALUE;
            for (var e : star.entrySet()) {
                double value = ((Number) e.getValue()).doubleValue();
                if (value < min) {
                    min = value;
                    weakest = e.getKey();
                }
            }
            return switch (weakest) {
                case "result" -> "result and measurable impact";
                case "action" -> "action: what you personally did";
                case "task" -> "task: your specific responsibility";
                default -> "situation and context";
            };
        }
        if ("DSA".equals(mode)) {
            if (passed != null && total != null && passed < total) {
                return "failing tests";
            }
            return followUps == 0 ? "complexity analysis" : "edge cases";
        }
        if ("LLD".equals(mode)) {
            List<String> order = List.of("concurrency", "extensibility", "design patterns", "SOLID");
            return order.get(Math.min(followUps, order.size() - 1));
        }
        if (!missing.isEmpty()) {
            return missing.getFirst();
        }
        return followUps == 0 ? "scaling" : "failures and debugging";
    }

    // ------------------------------------------------------------------ evaluation

    @SuppressWarnings("unchecked")
    Map<String, Object> evaluation(Map<String, Object> v, Random random) {
        String mode = str(v, "mode");
        List<Map<String, Object>> questions = (List<Map<String, Object>>) v.getOrDefault("mockQuestions", List.of());
        List<Map<String, Object>> turns = (List<Map<String, Object>>) v.getOrDefault("mockCandidateTurns", List.of());
        List<Map<String, Object>> rubric = (List<Map<String, Object>>) v.getOrDefault("mockCompetencies", List.of());
        List<String> integrity = list(v, "mockIntegrity");

        double avg = questions.stream().mapToDouble(q -> dbl(q.get("turnScore"), 5.0)).average().orElse(5.0);
        List<Map<String, Object>> competencies = new ArrayList<>();
        Map<String, Double> competencyScores = new LinkedHashMap<>();
        for (Map<String, Object> competency : rubric) {
            String name = str(competency, "competency");
            double bias = switch (name) {
                case "COMMUNICATION" -> 0.4;
                case "EDGE_CASES" -> -0.6;
                case "CODE_QUALITY" -> 0.1;
                case "DESIGN" -> -0.2;
                default -> 0.0;
            };
            List<Map<String, Object>> criteria = new ArrayList<>();
            double sum = 0;
            for (String id : (List<String>) competency.getOrDefault("criteria", List.of())) {
                int score = (int) Math.round(Math.max(0, Math.min(10, avg + bias + (random.nextDouble() - 0.5) * 1.6)));
                sum += score;
                criteria.add(Map.of("id", id, "score", score, "justification", justification(name, score)));
            }
            double competencyScore = criteria.isEmpty() ? avg : sum / criteria.size();
            competencyScores.put(name, competencyScore);
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (Map<String, Object> turn : evidenceTurns(turns, random, 2)) {
                String quote = quotable(str(turn, "content"), random);
                if (!quote.isBlank()) {
                    evidence.add(Map.of("quote", quote, "turnSeq", intValue(turn, "seq", 0)));
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("competency", name);
            result.put("criteria", criteria);
            result.put("rationale", rationale(name, competencyScore));
            result.put("evidence", evidence);
            competencies.add(result);
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(competencyScores.entrySet());
        ranked.sort(Map.Entry.comparingByValue());
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        for (int i = ranked.size() - 1; i >= 0 && strengths.size() < 2; i--) {
            strengths.add(strengthText(ranked.get(i).getKey()));
        }
        for (int i = 0; i < ranked.size() && gaps.size() < 2; i++) {
            gaps.add(gapText(ranked.get(i).getKey()));
        }
        while (strengths.size() < 2) {
            strengths.add("You engaged with every question and kept your answers on topic.");
        }
        while (gaps.size() < 2) {
            gaps.add("Answers would benefit from more concrete specifics and explicit trade-offs.");
        }

        List<Map<String, Object>> perQuestion = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();
        for (Map<String, Object> q : questions) {
            tags.addAll(list(q, "tags"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("position", intValue(q, "position", 0));
            String reference = str(q, "referenceMd");
            result.put("modelAnswerMd", reference.isBlank() ? genericModelAnswer(mode, str(q, "title")) : reference);
            result.put("keyMissedPoints", truncate(list(q, "missingPoints"), 6));
            result.put("star", "BEHAVIOURAL".equals(mode) ? starBreakdown((Map<String, Object>) q.get("star"), turns, random) : null);
            perQuestion.add(result);
        }

        List<Map<String, Object>> nextSteps = new ArrayList<>();
        List<String> tagList = new ArrayList<>(tags);
        for (int i = 0; nextSteps.size() < 3; i++) {
            String competency = ranked.isEmpty() ? "COMMUNICATION" : ranked.get(i % ranked.size()).getKey();
            String tag = tagList.isEmpty() ? null : tagList.get(i % tagList.size());
            nextSteps.add(nextStep(mode, competency, tag, i));
        }

        List<Map<String, Object>> flags = new ArrayList<>();
        for (String flag : integrity) {
            flags.add(Map.of("type", flag.contains("INJECTION") ? "PROMPT_INJECTION_ATTEMPT" : "EMPTY_OR_EVASIVE",
                    "detail", flag.length() > 280 ? flag.substring(0, 280) : flag));
            if (flags.size() == 5) {
                break;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary(mode, avg, ranked));
        out.put("competencies", competencies);
        out.put("strengths", strengths);
        out.put("gaps", gaps);
        out.put("perQuestion", perQuestion);
        out.put("nextSteps", nextSteps);
        out.put("integrityFlags", flags);
        return out;
    }

    private static List<Map<String, Object>> evidenceTurns(List<Map<String, Object>> turns, Random random, int max) {
        List<Map<String, Object>> candidates = new ArrayList<>(turns.stream()
                .filter(t -> str(t, "content").trim().split("\\s+").length >= 6).toList());
        java.util.Collections.shuffle(candidates, random);
        return candidates.subList(0, Math.min(max, candidates.size()));
    }

    private static Map<String, Object> starBreakdown(Map<String, Object> star, List<Map<String, Object>> turns, Random random) {
        Map<String, Object> out = new LinkedHashMap<>();
        String quote = turns.isEmpty() ? null : quotable(str(turns.get(random.nextInt(turns.size())), "content"), random);
        for (String part : List.of("situation", "task", "action", "result")) {
            int score = (int) Math.round(star == null ? 5 : dbl(star.get(part), 5));
            out.put(part, Map.of("score", score, "justification", starJustification(part, score),
                    "quote", quote == null || quote.isBlank() ? "" : quote));
        }
        return out;
    }

    private static String starJustification(String part, int score) {
        boolean good = score >= 7;
        return switch (part) {
            case "situation" -> good ? "Context was specific: team, stakes and timeline were clear." : "The context stayed vague; name the team, the stakes and the timeline.";
            case "task" -> good ? "Your own responsibility was explicit." : "It was unclear what you specifically owned.";
            case "action" -> good ? "Actions were concrete and clearly yours." : "Actions were described as 'we'; separate your own decisions.";
            default -> good ? "The outcome was measurable and tied back to the goal." : "The result lacked numbers or a clear before/after.";
        };
    }

    private static String justification(String competency, int score) {
        String level = score >= 8 ? "Strong" : score >= 6 ? "Solid" : score >= 4 ? "Partial" : "Weak";
        return level + " evidence for " + competency.toLowerCase(Locale.ROOT).replace('_', ' ') + " in the transcript.";
    }

    private static String rationale(String competency, double score) {
        String band = score >= 8 ? "consistently strong" : score >= 6 ? "generally solid with some gaps" : score >= 4 ? "inconsistent" : "not yet demonstrated";
        return switch (competency) {
            case "PROBLEM_SOLVING" -> "Problem decomposition was " + band + ": how quickly you reached a workable approach and justified it.";
            case "CODE_QUALITY" -> "Code and structure were " + band + ": naming, decomposition and readability of what you wrote.";
            case "COMMUNICATION" -> "Communication was " + band + ": clarity, structure and thinking aloud.";
            case "DESIGN" -> "Design reasoning was " + band + ": responsibilities, abstractions and trade-offs.";
            default -> "Edge-case and outcome thinking was " + band + ": boundaries, failure modes and measurable results.";
        };
    }

    private static String strengthText(String competency) {
        return switch (competency) {
            case "PROBLEM_SOLVING" -> "You broke the problem down methodically before committing to an approach.";
            case "CODE_QUALITY" -> "Your solution was readable, with clear names and sensible decomposition.";
            case "COMMUNICATION" -> "You explained your reasoning clearly and kept the interviewer in the loop.";
            case "DESIGN" -> "You identified the core entities and gave each a focused responsibility.";
            default -> "You considered boundaries and outcomes rather than only the happy path.";
        };
    }

    private static String gapText(String competency) {
        return switch (competency) {
            case "PROBLEM_SOLVING" -> "State the brute-force baseline first, then justify each optimisation with complexity.";
            case "CODE_QUALITY" -> "Code structure could be cleaner: extract helpers and avoid duplicated logic.";
            case "COMMUNICATION" -> "Structure answers explicitly (e.g. STAR or 'approach → complexity → edge cases').";
            case "DESIGN" -> "Discuss trade-offs and extension points, not only the class list.";
            default -> "Edge cases and measurable outcomes were under-explored; enumerate them explicitly.";
        };
    }

    private static Map<String, Object> nextStep(String mode, String competency, String tag, int index) {
        String topic = tag == null ? "this topic" : tag.replace('-', ' ');
        String action = switch (competency) {
            case "PROBLEM_SOLVING" -> "Solve 5 " + topic + " problems this week, writing the brute force and its complexity before optimising.";
            case "CODE_QUALITY" -> "Re-solve one problem from this session and refactor it for readability; compare with the model answer.";
            case "COMMUNICATION" -> "Record yourself answering one question from this session in under 3 minutes using a fixed structure.";
            case "DESIGN" -> "Design one more system in the " + topic + " area and write down 3 trade-offs you made.";
            default -> "For your next 3 practice answers, list the edge cases (or measurable results) before you start.";
        };
        String why = "Targets your weakest area in this session: " + competency.toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        String resource = switch (index % 3) {
            case 0 -> "PRACTICE";
            case 1 -> "MOCK".equals(mode) ? "READ" : "REFLECT";
            default -> "MOCK";
        };
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("action", action);
        step.put("why", why);
        step.put("resourceType", resource);
        step.put("relatedTag", tag);
        return step;
    }

    private static String summary(String mode, double avg, List<Map.Entry<String, Double>> ranked) {
        String level = avg >= 8 ? "a strong" : avg >= 6 ? "a solid" : avg >= 4 ? "a mixed" : "an early-stage";
        String best = ranked.isEmpty() ? "" : ranked.getLast().getKey().toLowerCase(Locale.ROOT).replace('_', ' ');
        String worst = ranked.isEmpty() ? "" : ranked.getFirst().getKey().toLowerCase(Locale.ROOT).replace('_', ' ');
        String round = switch (mode) {
            case "DSA" -> "coding";
            case "LLD" -> "low-level design";
            case "BEHAVIOURAL" -> "behavioural";
            default -> "resume deep-dive";
        };
        return "This was " + level + " " + round + " interview. Your clearest strength was " + best
                + ", while " + worst + " is where focused practice will move your score the most. "
                + "(Generated by the mock AI provider from heuristics over your answers.)";
    }

    private static String genericModelAnswer(String mode, String title) {
        return switch (mode) {
            case "BEHAVIOURAL" -> "**Model STAR outline for \"" + title + "\"**\n\n- **Situation:** one or two sentences of specific context (team, stakes, timeline).\n- **Task:** what *you* were responsible for.\n- **Action:** 2–4 concrete steps you personally took, including a decision and its trade-off.\n- **Result:** a measurable outcome, plus what you learned and would repeat.";
            case "RESUME_DEEP_DIVE" -> "**What a strong answer covers for \"" + title + "\"**\n\n- The problem the project solved and for whom.\n- The key design decision, the alternatives, and why this one won.\n- What you personally built and owned.\n- A failure or limitation, how you found it, and what you changed.\n- How it would evolve at 10× scale.";
            default -> "**Reference approach for \"" + title + "\"**\n\nState the brute force, identify the bottleneck, apply the right structure, then give time/space complexity and the edge cases you'd test.";
        };
    }

    // ------------------------------------------------------------------ resume

    Map<String, Object> resumeExtraction(Map<String, Object> v) {
        String text = str(v, "resumeText");
        String lower = text.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> skills = new ArrayList<>();
        for (String skill : SKILLS) {
            if (Pattern.compile("(?<![A-Za-z])" + Pattern.quote(skill.toLowerCase(Locale.ROOT)) + "(?![A-Za-z+#])")
                    .matcher(lower).find()) {
                skills.add(Map.of("name", skill, "category", category(skill)));
            }
        }
        List<Map<String, Object>> projects = projects(text);
        List<Map<String, Object>> experience = experience(text);
        Map<String, Object> out = new LinkedHashMap<>();
        boolean looksLikeResume = !skills.isEmpty() || !projects.isEmpty() || lower.contains("experience") || lower.contains("education");
        out.put("summary", looksLikeResume ? firstSentence(text) : "NOT_A_RESUME");
        out.put("skills", truncate(skills, 60));
        out.put("projects", truncate(projects, 10));
        out.put("experience", truncate(experience, 10));
        out.put("integrityFlags", List.of());
        return out;
    }

    private static String category(String skill) {
        return switch (skill) {
            case "Java", "Python", "JavaScript", "TypeScript", "C++", "Go", "Kotlin", "SQL" -> "LANGUAGE";
            case "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch" -> "DATABASE";
            case "AWS", "GCP", "Azure", "Kubernetes", "Terraform" -> "CLOUD";
            case "REST", "gRPC", "GraphQL", "Microservices" -> "CONCEPT";
            case "Docker", "Git", "Linux", "Jenkins", "GitHub Actions", "JUnit", "Selenium", "Kafka", "RabbitMQ" -> "TOOL";
            default -> "FRAMEWORK";
        };
    }

    private List<Map<String, Object>> projects(String text) {
        List<Map<String, Object>> projects = new ArrayList<>();
        List<String> section = section(text, "project");
        Map<String, Object> current = null;
        List<String> details = new ArrayList<>();
        for (String raw : section) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            boolean bullet = line.matches("^[-•*▪◦●–].*");
            if (!bullet && line.length() <= 80 && !line.endsWith(".")) {
                if (current != null) {
                    finishProject(current, details);
                    projects.add(current);
                }
                current = new LinkedHashMap<>();
                current.put("name", line.replaceAll("[|:–—-].*$", "").trim().isEmpty() ? line : line.replaceAll("\\s*[|:–—].*$", "").trim());
                details = new ArrayList<>();
                details.add(line);
            } else if (current != null) {
                details.add(line.replaceFirst("^[-•*▪◦●–]\\s*", ""));
            }
        }
        if (current != null) {
            finishProject(current, details);
            projects.add(current);
        }
        projects.removeIf(p -> str(p, "name").length() < 3);
        return projects;
    }

    private static void finishProject(Map<String, Object> project, List<String> details) {
        List<String> highlights = details.size() > 1 ? details.subList(1, details.size()) : List.of();
        String summary = highlights.isEmpty() ? details.getFirst() : highlights.getFirst();
        project.put("summary", summary.length() > 480 ? summary.substring(0, 480) : summary);
        List<String> tech = new ArrayList<>();
        String joined = String.join(" ", details).toLowerCase(Locale.ROOT);
        for (String skill : SKILLS) {
            if (joined.contains(skill.toLowerCase(Locale.ROOT)) && tech.size() < 15) {
                tech.add(skill);
            }
        }
        project.put("techStack", tech);
        project.put("highlights", truncate(highlights.stream().map(h -> h.length() > 190 ? h.substring(0, 190) : h).toList(), 6));
    }

    private static List<Map<String, Object>> experience(String text) {
        List<Map<String, Object>> experience = new ArrayList<>();
        Pattern atPattern = Pattern.compile("^(.{2,60}?)\\s+(?:at|@|—|-|,)\\s+(.{2,60}?)(?:\\s*[|(].*)?$");
        for (String raw : section(text, "experience")) {
            String line = raw.trim();
            Matcher matcher = atPattern.matcher(line);
            if (!line.matches("^[-•*].*") && matcher.matches() && line.length() < 120) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("company", matcher.group(2).trim());
                entry.put("title", matcher.group(1).trim());
                entry.put("start", null);
                entry.put("end", null);
                entry.put("highlights", List.of());
                experience.add(entry);
            }
        }
        return experience;
    }

    /** Lines after a heading containing {@code keyword} until the next all-caps / short heading-like line. */
    private static List<String> section(String text, String keyword) {
        List<String> lines = List.of(text.replace("\r", "").split("\n"));
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean heading = trimmed.length() <= 30 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT)) && trimmed.matches(".*[A-Z].*");
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (normalized.matches("^" + keyword + "[a-z ]*:?$") || (heading && normalized.contains(keyword))) {
                inside = true;
                continue;
            }
            if (inside && heading && !normalized.contains(keyword)) {
                break;
            }
            if (inside) {
                out.add(line);
            }
        }
        return out;
    }

    Map<String, Object> resumeQuestions(Map<String, Object> v) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) v.getOrDefault("projectList", List.of());
        int count = intValue(v, "count", 4);
        List<String> angles = List.of("TRADEOFFS", "SCALING", "FAILURES", "OWNERSHIP", "TESTING");
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < count && !projects.isEmpty(); i++) {
            Map<String, Object> project = projects.get(i % projects.size());
            String name = str(project, "name");
            List<String> tech = list(project, "techStack");
            String stack = tech.isEmpty() ? "that stack" : tech.getFirst();
            String angle = angles.get(i % angles.size());
            String question = switch (angle) {
                case "TRADEOFFS" -> "In " + name + ", why did you choose " + stack + "? What alternatives did you consider, and what did you give up?";
                case "SCALING" -> "Suppose " + name + " had 10× more users tomorrow. What would break first, and how would you change the design?";
                case "FAILURES" -> "Tell me about something that went wrong while building or running " + name + ". How did you find the root cause?";
                case "OWNERSHIP" -> "Which part of " + name + " did you personally own end to end, and what decisions did you make there?";
                default -> "How did you test " + name + "? What kinds of bugs did your tests miss?";
            };
            questions.add(Map.of("projectName", name, "angle", angle, "question", question,
                    "expectedSignals", List.of("Specific technical details rather than generalities",
                            "Clear personal ownership and decisions", "Honest trade-offs or limitations")));
        }
        return Map.of("questions", questions);
    }

    // ------------------------------------------------------------------ helpers

    private static String firstSentence(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        int dot = flat.indexOf(". ");
        String sentence = dot > 20 ? flat.substring(0, dot + 1) : flat;
        return sentence.length() > 300 ? sentence.substring(0, 300) : sentence;
    }

    /** A verbatim, sentence-sized excerpt of the candidate's own words (≤ 160 chars). */
    static String quotable(String text, Random random) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> sentences = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?])\\s+|\\n+")) {
            String trimmed = sentence.trim();
            if (trimmed.split("\\s+").length >= 5) {
                sentences.add(trimmed);
            }
        }
        if (sentences.isEmpty()) {
            String trimmed = text.trim();
            return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
        }
        String chosen = sentences.get(random.nextInt(sentences.size()));
        return chosen.length() > 160 ? chosen.substring(0, 160) : chosen;
    }

    private static double markers(String text, String... markers) {
        int hits = 0;
        for (String marker : markers) {
            if (text.contains(marker)) {
                hits++;
            }
        }
        return 2.0 + hits * 2.2;
    }

    private static double clamp(double value) {
        return Math.round(Math.max(0, Math.min(10, value)) * 10) / 10.0;
    }

    private static Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String word : (text == null ? "" : text.toLowerCase(Locale.ROOT)).split("[^a-z0-9+#]+")) {
            if (word.length() >= 3 && !STOPWORDS.contains(word)) {
                out.add(word.length() > 5 ? word.substring(0, 5) : word); // crude stemming
            }
        }
        return out;
    }

    private static <T> List<T> truncate(Collection<T> items, int max) {
        return new ArrayList<>(items).subList(0, Math.min(max, items.size()));
    }

    private static String pick(Random random, List<String> options) {
        return options.get(random.nextInt(options.size()));
    }

    static String str(Map<String, Object> v, String key) {
        Object value = v.get(key);
        return value == null ? "" : value.toString();
    }

    private static int intValue(Map<String, Object> v, String key, int fallback) {
        Object value = v.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Integer intOrNull(Map<String, Object> v, String key) {
        Object value = v.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static double dbl(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> v, String key) {
        Object value = v.get(key);
        if (value instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Mock provider could not serialise its output", e);
        }
    }
}
