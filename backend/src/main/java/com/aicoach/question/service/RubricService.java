package com.aicoach.question.service;

import com.aicoach.audit.service.AuditService;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.FieldValidationException;
import com.aicoach.question.domain.Rubric;
import com.aicoach.question.dto.QuestionDtos.CreateRubricRequest;
import com.aicoach.question.dto.QuestionDtos.RubricResponse;
import com.aicoach.question.dto.RubricDefinition;
import com.aicoach.question.repository.RubricRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned rubrics: new versions are immutable rows; exactly one version per mode is active. */
@Service
public class RubricService {

    private final RubricRepository rubrics;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Validator validator;

    public RubricService(RubricRepository rubrics, AuditService audit, ObjectMapper json, Validator validator) {
        this.rubrics = rubrics;
        this.audit = audit;
        this.json = json;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public Rubric active(String key) {
        return rubrics.findByKeyAndActiveTrue(key)
                .orElseThrow(() -> new AppException(ErrorCode.NO_QUESTIONS_AVAILABLE, "No active rubric for " + key));
    }

    @Transactional(readOnly = true)
    public Rubric require(UUID id) {
        return rubrics.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Rubric not found"));
    }

    public RubricDefinition definition(Rubric rubric) {
        return json.convertValue(rubric.getDefinition(), RubricDefinition.class);
    }

    @Transactional(readOnly = true)
    public List<RubricResponse> list() {
        return rubrics.findAllByOrderByKeyAscVersionDesc().stream().map(RubricService::view).toList();
    }

    @Transactional
    public RubricResponse create(UUID actorId, CreateRubricRequest request) {
        JsonNode definition = validate(request.definition());
        Rubric rubric = rubrics.saveAndFlush(new Rubric(request.key(), rubrics.maxVersion(request.key()) + 1, definition,
                request.changelog() == null ? "" : request.changelog(), actorId));
        audit.record(actorId, "RUBRIC_CREATED", "RUBRIC", rubric.getId(), null,
                Map.of("key", rubric.getKey(), "version", rubric.getVersion()));
        return view(rubric);
    }

    @Transactional
    public RubricResponse activate(UUID actorId, UUID id) {
        Rubric rubric = require(id);
        rubrics.findByKeyAndActiveTrue(rubric.getKey()).ifPresent(current -> {
            current.deactivate();
            rubrics.flush();
        });
        rubric.activate();
        rubrics.flush();
        audit.record(actorId, "RUBRIC_ACTIVATED", "RUBRIC", id, null,
                Map.of("key", rubric.getKey(), "version", rubric.getVersion()));
        return view(rubric);
    }

    /** Validates a definition: schema, unique competencies, weights summing to 1 (±0.001). */
    public JsonNode validate(JsonNode definition) {
        RubricDefinition typed;
        try {
            typed = json.convertValue(definition, RubricDefinition.class);
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("body.definition", "is not a valid rubric definition");
        }
        Set<ConstraintViolation<RubricDefinition>> violations = validator.validate(typed);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new FieldValidationException("body.definition." + first.getPropertyPath(), first.getMessage());
        }
        if (Math.abs(typed.totalWeight() - 1.0) > 0.001) {
            throw new FieldValidationException("body.definition.competencies", "weights must sum to 1");
        }
        if (typed.competencies().stream().map(c -> c.competency()).distinct().count() != typed.competencies().size()) {
            throw new FieldValidationException("body.definition.competencies", "contains a competency twice");
        }
        return json.valueToTree(typed);
    }

    static RubricResponse view(Rubric r) {
        return new RubricResponse(r.getId(), r.getKey(), r.getVersion(), r.isActive(), r.getDefinition(), r.getChangelog(),
                r.getCreatedAt());
    }
}
