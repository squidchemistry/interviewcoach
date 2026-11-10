package com.aicoach.question.service;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.web.PageResponse;
import com.aicoach.execution.service.HarnessBuilder;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionStatus;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.domain.QuestionPack;
import com.aicoach.question.dto.QuestionDetails.BehaviouralDetail;
import com.aicoach.question.dto.QuestionDetails.DsaDetail;
import com.aicoach.question.dto.QuestionDetails.LldDetail;
import com.aicoach.question.dto.QuestionDtos.BehaviouralView;
import com.aicoach.question.dto.QuestionDtos.DsaView;
import com.aicoach.question.dto.QuestionDtos.LldView;
import com.aicoach.question.dto.QuestionDtos.PackResponse;
import com.aicoach.question.dto.QuestionDtos.QuestionResponse;
import com.aicoach.question.dto.QuestionDtos.QuestionSummaryResponse;
import com.aicoach.question.dto.QuestionDtos.SampleTest;
import com.aicoach.question.dto.QuestionDtos.SimilarQuestionResponse;
import com.aicoach.question.repository.QuestionEmbeddingRepository;
import com.aicoach.question.repository.QuestionPackRepository;
import com.aicoach.question.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read side of the question bank, for candidates and for the interview engine. */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuestionPackRepository packs;
    private final QuestionEmbeddingRepository embeddings;
    private final HarnessBuilder harness;
    private final ObjectMapper json;

    public QuestionService(QuestionRepository questions, QuestionPackRepository packs,
            QuestionEmbeddingRepository embeddings, HarnessBuilder harness, ObjectMapper json) {
        this.questions = questions;
        this.packs = packs;
        this.embeddings = embeddings;
        this.harness = harness;
        this.json = json;
    }

    public record SearchFilter(QuestionType type, Difficulty difficulty, String tag, String company, String query) {
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryResponse> search(SearchFilter filter, int page, int pageSize) {
        return PageResponse.of(questions.search(QuestionStatus.PUBLISHED.name(), name(filter.type()),
                name(filter.difficulty()), blankToNull(filter.tag()), blankToNull(filter.company()),
                blankToNull(filter.query()), PageRequest.of(page - 1, pageSize)), QuestionService::summary);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getPublished(String idOrSlug) {
        Question question = findByIdOrSlug(idOrSlug)
                .filter(q -> q.getStatus() == QuestionStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Question not found"));
        return publicView(question);
    }

    @Transactional(readOnly = true)
    public List<SimilarQuestionResponse> similar(String idOrSlug, int k) {
        Question source = findByIdOrSlug(idOrSlug)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Question not found"));
        var neighbours = embeddings.similarTo(source.getId(), k);
        Map<UUID, Question> byId = questions.findAllById(neighbours.stream().map(n -> n.id()).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        return neighbours.stream().filter(n -> byId.containsKey(n.id()))
                .map(n -> new SimilarQuestionResponse(summary(byId.get(n.id())), Math.round(n.similarity() * 1000) / 1000.0))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PackResponse> packs() {
        return packs.findAllByOrderByNameAsc().stream()
                .map(p -> new PackResponse(p.getSlug(), p.getName(), p.getDescription(), p.getMode(), p.getCompany(),
                        p.isPro(), p.getQuestionSlugs().size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> tags() {
        return questions.distinctTags();
    }

    // ----------------------------------------------------------- used by the interview engine

    @Transactional(readOnly = true)
    public List<Question> published(QuestionType type) {
        return questions.findPublishedByType(type);
    }

    @Transactional(readOnly = true)
    public Question require(UUID id) {
        return questions.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Question not found"));
    }

    @Transactional(readOnly = true)
    public QuestionPack pack(String slug) {
        return packs.findBySlug(slug).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Pack not found"));
    }

    @Transactional(readOnly = true)
    public List<Question> bySlugs(List<String> slugs) {
        return questions.findBySlugIn(slugs);
    }

    /** Titles of the nearest neighbours of a question: retrieval context (RAG) for interviewer follow-ups. */
    @Transactional(readOnly = true)
    public List<Question> neighbours(UUID id, int k) {
        return questions.findAllById(embeddings.similarTo(id, k).stream().map(n -> n.id()).toList());
    }

    public DsaDetail dsa(Question question) {
        return json.convertValue(question.getDetail(), DsaDetail.class);
    }

    public LldDetail lld(Question question) {
        return json.convertValue(question.getDetail(), LldDetail.class);
    }

    public BehaviouralDetail behavioural(Question question) {
        return json.convertValue(question.getDetail(), BehaviouralDetail.class);
    }

    public List<String> expectedPoints(Question question) {
        return switch (question.getType()) {
            case DSA -> dsa(question).expectedPoints();
            case LLD -> lld(question).expectedPoints();
            case BEHAVIOURAL -> behavioural(question).expectedPoints();
        };
    }

    public QuestionResponse publicView(Question question) {
        DsaView dsa = null;
        LldView lld = null;
        BehaviouralView behavioural = null;
        switch (question.getType()) {
            case DSA -> {
                DsaDetail d = dsa(question);
                Map<String, String> starters = new LinkedHashMap<>();
                harness.starterCode(d).forEach((lang, code) -> starters.put(lang.name(), code));
                dsa = new DsaView(d.functionName(), d.params(), d.returnType(),
                        d.samples().stream().map(t -> new SampleTest(t.input(), t.expected())).toList(),
                        d.testCases().size() - d.samples().size(), starters);
            }
            case LLD -> {
                LldDetail d = lld(question);
                lld = new LldView(d.requirements(), d.focusAreas());
            }
            case BEHAVIOURAL -> {
                BehaviouralDetail d = behavioural(question);
                behavioural = new BehaviouralView(d.competency(), d.leadershipPrinciple());
            }
        }
        return new QuestionResponse(question.getId(), question.getSlug(), question.getType(), question.getTitle(),
                question.getBodyMd(), question.getDifficulty(), question.getTags(), question.getCompanies(), dsa, lld,
                behavioural);
    }

    static QuestionSummaryResponse summary(Question q) {
        return new QuestionSummaryResponse(q.getId(), q.getSlug(), q.getType(), q.getTitle(), q.getDifficulty(),
                q.getTags(), q.getCompanies());
    }

    private java.util.Optional<Question> findByIdOrSlug(String idOrSlug) {
        try {
            return questions.findById(UUID.fromString(idOrSlug));
        } catch (IllegalArgumentException notUuid) {
            return questions.findBySlug(idOrSlug);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
