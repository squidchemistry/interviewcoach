package com.aicoach.question.service;

import com.aicoach.audit.service.AuditService;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.FieldValidationException;
import com.aicoach.common.web.PageResponse;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.dto.QuestionDetails.BehaviouralDetail;
import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.question.dto.QuestionDetails.LldDetail;
import com.aicoach.question.dto.QuestionDtos.AdminQuestionResponse;
import com.aicoach.question.dto.QuestionDtos.UpsertQuestionRequest;
import com.aicoach.question.repository.QuestionEmbeddingRepository;
import com.aicoach.question.repository.QuestionRepository;
import com.aicoach.question.service.QuestionService.SearchFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for the question bank: per-type validation, optimistic versioning, audit trail, re-embedding. */
@Service
public class QuestionAdminService {

    private final QuestionRepository questions;
    private final QuestionEmbeddingRepository embeddings;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Validator validator;

    public QuestionAdminService(QuestionRepository questions, QuestionEmbeddingRepository embeddings, AuditService audit,
            ObjectMapper json, Validator validator) {
        this.questions = questions;
        this.embeddings = embeddings;
        this.audit = audit;
        this.json = json;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminQuestionResponse> search(SearchFilter filter, QuestionStatus status, int page, int pageSize) {
        return PageResponse.of(questions.search(status == null ? null : status.name(),
                filter.type() == null ? null : filter.type().name(),
                filter.difficulty() == null ? null : filter.difficulty().name(), filter.tag(), filter.company(),
                filter.query() == null || filter.query().isBlank() ? null : filter.query(),
                PageRequest.of(page - 1, pageSize)), QuestionAdminService::view);
    }

    @Transactional(readOnly = true)
    public AdminQuestionResponse get(UUID id) {
        return view(require(id));
    }

    @Transactional
    public AdminQuestionResponse create(UUID actorId, UpsertQuestionRequest request) {
        if (questions.existsBySlug(request.slug())) {
            throw new FieldValidationException("body.slug", "is already used by another question");
        }
        Question question = Question.create(request.type(), request.slug(), "ADMIN", actorId);
        apply(question, request);
        Question saved = questions.saveAndFlush(question);
        audit.record(actorId, "QUESTION_CREATED", "QUESTION", saved.getId(), null, Map.of("slug", saved.getSlug()));
        return view(saved);
    }

    @Transactional
    public AdminQuestionResponse update(UUID actorId, UUID id, UpsertQuestionRequest request) {
        Question question = require(id);
        if (request.type() != question.getType() || !request.slug().equals(question.getSlug())) {
            throw new AppException(ErrorCode.CONFLICT, "A question's type and slug cannot change");
        }
        if (request.expectedVersion() != null && request.expectedVersion() != question.getVersion()) {
            throw new AppException(ErrorCode.STATE_CONFLICT, "This question was changed by someone else; reload and retry");
        }
        Map<String, Object> before = Map.of("title", question.getTitle(), "status", question.getStatus().name(),
                "version", question.getVersion());
        apply(question, request);
        try {
            questions.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new AppException(ErrorCode.STATE_CONFLICT, "This question was changed by someone else; reload and retry");
        }
        embeddings.clear(id);
        audit.record(actorId, "QUESTION_UPDATED", "QUESTION", id, before,
                Map.of("title", question.getTitle(), "status", question.getStatus().name()));
        return view(question);
    }

    @Transactional
    public AdminQuestionResponse changeStatus(UUID actorId, UUID id, QuestionStatus status) {
        Question question = require(id);
        QuestionStatus previous = question.getStatus();
        if (status == QuestionStatus.PUBLISHED) {
            validateDetail(question.getType(), question.getDetail());
        }
        question.changeStatus(status);
        questions.flush();
        audit.record(actorId, "QUESTION_STATUS_CHANGED", "QUESTION", id, Map.of("status", previous.name()),
                Map.of("status", status.name()));
        return view(question);
    }

    private void apply(Question question, UpsertQuestionRequest request) {
        JsonNode normalized = validateDetail(request.type(), request.detail());
        question.update(request.title().trim(), request.bodyMd(), request.difficulty(), request.status(),
                request.tags(), request.companies(), normalized);
    }

    /** Validates type-specific details (e.g. DSA: ≥ 2 sample and ≥ 3 hidden tests, exactly 3 hints). */
    JsonNode validateDetail(com.aicoach.question.domain.QuestionEnums.QuestionType type, JsonNode detail) {
        Object typed;
        try {
            typed = switch (type) {
                case DSA -> json.convertValue(detail, DsaDetail.class);
                case LLD -> json.convertValue(detail, LldDetail.class);
                case BEHAVIOURAL -> json.convertValue(detail, BehaviouralDetail.class);
            };
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("body.detail", "does not match the " + type + " detail format");
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(typed);
        if (!violations.isEmpty()) {
            ConstraintViolation<Object> first = violations.iterator().next();
            throw new FieldValidationException("body.detail." + first.getPropertyPath(), first.getMessage());
        }
        if (typed instanceof DsaDetail dsa) {
            long samples = dsa.testCases().stream().filter(t -> t.sample()).count();
            long hidden = dsa.testCases().size() - samples;
            if (samples < 2 || hidden < 3) {
                throw new FieldValidationException("body.detail.testCases", "needs at least 2 sample and 3 hidden tests");
            }
            for (var test : dsa.testCases()) {
                if (!test.input().isArray() || test.input().size() != dsa.params().size()) {
                    throw new FieldValidationException("body.detail.testCases",
                            "each input must be an array with one value per parameter");
                }
            }
        }
        return json.valueToTree(typed);
    }

    private Question require(UUID id) {
        return questions.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Question not found"));
    }

    static AdminQuestionResponse view(Question q) {
        return new AdminQuestionResponse(q.getId(), q.getSlug(), q.getType(), q.getTitle(), q.getBodyMd(),
                q.getDifficulty(), q.getStatus(), q.getTags(), q.getCompanies(), q.getDetail(), q.getSource(),
                q.getVersion(), q.getUpdatedAt());
    }
}
