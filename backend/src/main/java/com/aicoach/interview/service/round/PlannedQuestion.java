package com.aicoach.interview.service.round;

import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/** A question chosen for the next position: bank question (with id) or generated (resume deep-dive, id null). */
public record PlannedQuestion(UUID questionId, String title, String promptMd, Difficulty difficulty, List<String> tags,
        JsonNode context) {
}
