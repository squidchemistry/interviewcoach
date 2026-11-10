package com.aicoach.interview.service;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.RateLimitedException;
import com.aicoach.common.ratelimit.RateLimiter;
import com.aicoach.common.security.Hashing;
import com.aicoach.common.web.PageResponse;
import com.aicoach.execution.service.CodeRunner;
import com.aicoach.execution.service.CodeRunner.RunResult;
import com.aicoach.interview.domain.Evaluation;
import com.aicoach.interview.domain.InterviewEnums.EvaluationStatus;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.SessionStatus;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.domain.SessionQuestion;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.dto.InterviewDtos.CodeRunRequest;
import com.aicoach.interview.dto.InterviewDtos.CodeRunResponse;
import com.aicoach.interview.dto.InterviewDtos.CommandResponse;
import com.aicoach.interview.dto.InterviewDtos.CompetencyResponse;
import com.aicoach.interview.dto.InterviewDtos.CreateSessionRequest;
import com.aicoach.interview.dto.InterviewDtos.EvaluationResponse;
import com.aicoach.interview.dto.InterviewDtos.EvaluatorRef;
import com.aicoach.interview.dto.InterviewDtos.EvidenceResponse;
import com.aicoach.interview.dto.InterviewDtos.PublicCompetency;
import com.aicoach.interview.dto.InterviewDtos.PublicQuestion;
import com.aicoach.interview.dto.InterviewDtos.PublicReportResponse;
import com.aicoach.interview.dto.InterviewDtos.ReportResponse;
import com.aicoach.interview.dto.InterviewDtos.RubricRef;
import com.aicoach.interview.dto.InterviewDtos.SessionDetailResponse;
import com.aicoach.interview.dto.InterviewDtos.SessionQuestionResponse;
import com.aicoach.interview.dto.InterviewDtos.SessionSummaryResponse;
import com.aicoach.interview.dto.InterviewDtos.ShareLinkResponse;
import com.aicoach.interview.dto.InterviewDtos.SubmitAnswerRequest;
import com.aicoach.interview.dto.InterviewDtos.TelemetryEvent;
import com.aicoach.interview.dto.InterviewDtos.TelemetryRequest;
import com.aicoach.interview.dto.InterviewDtos.TestResultResponse;
import com.aicoach.interview.dto.InterviewDtos.TurnResponse;
import com.aicoach.interview.repository.EvaluationRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository.CompetencyScoreRow;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.interview.repository.SessionRepository;
import com.aicoach.interview.repository.ShareLinkRepository;
import com.aicoach.interview.repository.ShareLinkRepository.ShareLink;
import com.aicoach.interview.repository.TurnRepository;
import com.aicoach.interview.service.engine.Effect;
import com.aicoach.interview.service.engine.EffectRunner;
import com.aicoach.interview.service.engine.InterviewEngine;
import com.aicoach.interview.service.engine.SessionCommand;
import com.aicoach.interview.service.round.ResumeDeepDiveRound;
import com.aicoach.interview.service.stream.SessionEventStream;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.domain.QuestionPack;
import com.aicoach.question.domain.Rubric;
import com.aicoach.question.dto.RubricDefinition;
import com.aicoach.question.service.QuestionService;
import com.aicoach.question.service.RubricService;
import com.aicoach.resume.service.ResumeService;
import com.aicoach.user.domain.Plan;
import com.aicoach.user.domain.Profile;
import com.aicoach.user.domain.TargetRole;
import com.aicoach.user.domain.User;
import com.aicoach.user.repository.ProfileRepository;
import com.aicoach.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application service of the interview feature: creation (quota, Pro gating, resume plan), the user commands (routed
 * through {@link InterviewEngine}), code execution, the report and share links. Controllers only talk to this class.
 */
@Service
public class InterviewService {

    static final AppProperties.Bucket CODE_RUN_BUCKET = new AppProperties.Bucket(20, Duration.ofMinutes(1));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;
    private final TurnRepository turns;
    private final EvaluationRepository evaluations;
    private final InterviewJdbcRepository jdbc;
    private final ShareLinkRepository shareLinks;
    private final InterviewEngine engine;
    private final EffectRunner effects;
    private final SessionEventStream stream;
    private final QuestionService questions;
    private final RubricService rubrics;
    private final ResumeService resumes;
    private final ResumeDeepDiveRound resumeRound;
    private final CodeRunner codeRunner;
    private final RateLimiter rateLimiter;
    private final UserService users;
    private final ProfileRepository profiles;
    private final InterviewProperties properties;
    private final AppProperties app;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    public InterviewService(SessionRepository sessions, SessionQuestionRepository sessionQuestions, TurnRepository turns,
            EvaluationRepository evaluations, InterviewJdbcRepository jdbc, ShareLinkRepository shareLinks,
            InterviewEngine engine, EffectRunner effects, SessionEventStream stream, QuestionService questions,
            RubricService rubrics, ResumeService resumes, ResumeDeepDiveRound resumeRound, CodeRunner codeRunner,
            RateLimiter rateLimiter, UserService users, ProfileRepository profiles, InterviewProperties properties,
            AppProperties app, ObjectMapper json, PlatformTransactionManager transactionManager, Clock clock) {
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.turns = turns;
        this.evaluations = evaluations;
        this.jdbc = jdbc;
        this.shareLinks = shareLinks;
        this.engine = engine;
        this.effects = effects;
        this.stream = stream;
        this.questions = questions;
        this.rubrics = rubrics;
        this.resumes = resumes;
        this.resumeRound = resumeRound;
        this.codeRunner = codeRunner;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.profiles = profiles;
        this.properties = properties;
        this.app = app;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ------------------------------------------------------------------ create

    public SessionSummaryResponse create(UUID userId, CreateSessionRequest request) {
        User user = users.getActive(userId);
        boolean pro = user.getPlan() == Plan.PRO;
        InterviewMode mode = request.mode();
        String period = period();
        if (!pro && jdbc.interviewsStarted(userId, period) >= properties.freeMonthlyInterviews()) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED, "The free plan includes " + properties.freeMonthlyInterviews()
                    + " interviews a month. Upgrade to Pro for unlimited practice.");
        }
        if (Boolean.TRUE.equals(request.voice()) && !pro) {
            throw new AppException(ErrorCode.PRO_REQUIRED, "Voice interviews are a Pro feature");
        }
        int count = questionCount(mode, request.questionCount());
        TargetRole role = request.targetRole() != null ? request.targetRole()
                : profiles.findById(userId).map(Profile::getTargetRole).orElse(TargetRole.SDE_1);
        Difficulty difficulty = request.startDifficulty() != null ? request.startDifficulty()
                : role == TargetRole.SDE_2 ? Difficulty.MEDIUM : Difficulty.EASY;
        String packSlug = blankToNull(request.packSlug());
        if (packSlug != null) {
            QuestionPack pack = questions.pack(packSlug);
            if (mode == InterviewMode.RESUME_DEEP_DIVE || pack.getMode() != QuestionType.valueOf(mode.name())) {
                throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "The pack '" + pack.getName() + "' is for "
                        + pack.getMode() + " interviews");
            }
            if (pack.isPro() && !pro) {
                throw new AppException(ErrorCode.PRO_REQUIRED, "The " + pack.getName() + " pack is a Pro feature");
            }
        }
        long seed = RANDOM.nextLong();
        UUID resumeId = null;
        JsonNode plan = json.createObjectNode();
        if (mode == InterviewMode.RESUME_DEEP_DIVE) {
            ResumeService.ParsedForInterview parsed = resumes.parsedFor(userId, request.resumeId());
            resumeId = parsed.resumeId();
            ObjectNode prepared = resumeRound.preparePlan(parsed.parsed(), count, ResumeDeepDiveRound.context(userId, seed));
            if (prepared.path("questions").isEmpty()) {
                throw new AppException(ErrorCode.NO_QUESTIONS_AVAILABLE, "No questions could be prepared from your resume");
            }
            plan = prepared;
        } else if (questions.published(QuestionType.valueOf(mode.name())).isEmpty()) {
            throw new AppException(ErrorCode.NO_QUESTIONS_AVAILABLE, "No " + mode + " questions are published yet");
        }
        Rubric rubric = rubrics.active(mode.name());
        InterviewSession session = new InterviewSession(userId, mode, role, blankToNull(request.company()), packSlug,
                resumeId, rubric.getId(), Boolean.TRUE.equals(request.voice()), count, difficulty, seed);
        session.setPlan(plan);
        InterviewSession saved = tx.execute(status -> {
            InterviewSession s = sessions.save(session);
            jdbc.incrementUsage(userId, period);
            return s;
        });
        return summary(saved, null);
    }

    static int questionCount(InterviewMode mode, Integer requested) {
        boolean technical = mode == InterviewMode.DSA || mode == InterviewMode.LLD;
        int min = technical ? 1 : 3;
        int max = technical ? 3 : 6;
        int value = requested == null ? (technical ? 2 : 4) : requested;
        if (value < min || value > max) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, mode + " interviews have " + min + "-" + max + " questions");
        }
        return value;
    }

    String period() {
        return YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).toString();
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<SessionSummaryResponse> list(UUID userId, SessionStatus status, InterviewMode mode, int page,
            int pageSize) {
        var result = sessions.findForUser(userId, status, mode, PageRequest.of(page - 1, pageSize));
        Map<UUID, Evaluation> evals = evaluations.findBySessionIdIn(result.map(InterviewSession::getId).toList()).stream()
                .collect(Collectors.toMap(Evaluation::getSessionId, Function.identity()));
        return PageResponse.of(result, s -> summary(s, evals.get(s.getId())));
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> recent(UUID userId, int limit) {
        return list(userId, null, null, 1, limit).items();
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse detail(UUID userId, UUID sessionId) {
        InterviewSession session = owned(userId, sessionId);
        Evaluation evaluation = evaluations.findBySessionId(sessionId).orElse(null);
        return new SessionDetailResponse(summary(session, evaluation),
                sessionQuestions.findBySessionIdOrderByPosition(sessionId).stream().map(InterviewService::question).toList(),
                turns.findBySessionIdOrderBySeq(sessionId).stream().map(InterviewService::turn).toList(),
                stream.lastId(sessionId), evaluation == null ? null : evaluation.getStatus().name(),
                codeRunner.executesCode());
    }

    // ------------------------------------------------------------------ commands

    public CommandResponse start(UUID userId, UUID sessionId) {
        return command(sessionId, userId, new SessionCommand.Start());
    }

    public CommandResponse submitAnswer(UUID userId, UUID sessionId, SubmitAnswerRequest request) {
        InterviewSession session = ownedReadOnly(userId, sessionId);
        if (request.code() != null && session.getMode() != InterviewMode.DSA) {
            throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "Code attachments are only accepted in coding rounds");
        }
        if (request.diagram() != null && session.getMode() != InterviewMode.LLD) {
            throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "Diagrams are only accepted in design rounds");
        }
        if (request.inputMode() == InputMode.VOICE && !session.isVoiceEnabled()) {
            throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "Voice is not enabled for this interview");
        }
        return command(sessionId, userId, new SessionCommand.SubmitAnswer(request.clientMessageId(),
                request.content().strip(), request.inputMode() == null ? InputMode.TEXT : request.inputMode(),
                request.code() == null ? null : request.code().source(),
                request.code() == null ? null : request.code().language().name(),
                request.diagram() == null ? null : request.diagram().mermaid()));
    }

    public CommandResponse hint(UUID userId, UUID sessionId, int level) {
        return command(sessionId, userId, new SessionCommand.RequestHint(level));
    }

    public CommandResponse retryTurn(UUID userId, UUID sessionId) {
        return command(sessionId, userId, new SessionCommand.RetryTurn());
    }

    public CommandResponse end(UUID userId, UUID sessionId) {
        return command(sessionId, userId, new SessionCommand.EndEarly());
    }

    public CommandResponse abandon(UUID userId, UUID sessionId) {
        return command(sessionId, userId, new SessionCommand.Abandon());
    }

    /** Client integrity signals: large pastes and leaving the tab. Context for the report, never a penalty. */
    public void telemetry(UUID userId, UUID sessionId, TelemetryRequest request) {
        List<String> signals = new ArrayList<>();
        for (TelemetryEvent e : request.events()) {
            switch (e.type()) {
                case "PASTE" -> {
                    if (e.size() != null && e.size() >= 200) {
                        signals.add("Pasted " + e.size() + " characters at " + e.at());
                    }
                }
                case "TAB_HIDDEN" -> signals.add("Left the interview tab at " + e.at());
                default -> {
                    // TAB_VISIBLE only closes a TAB_HIDDEN interval; nothing to record on its own
                }
            }
        }
        if (!signals.isEmpty()) {
            engine.dispatch(sessionId, userId, new SessionCommand.RecordIntegrity(signals));
        } else {
            ownedReadOnly(userId, sessionId);
        }
    }

    private CommandResponse command(UUID sessionId, UUID userId, SessionCommand command) {
        InterviewSession s = engine.dispatch(sessionId, userId, command).session();
        return new CommandResponse(s.getStatus(), s.getStage(), s.isProcessing(), stream.lastId(sessionId));
    }

    // ------------------------------------------------------------------ code execution (DSA)

    public CodeRunResponse runCode(UUID userId, UUID sessionId, CodeRunRequest request, boolean submit) {
        var decision = rateLimiter.tryConsume("code:" + userId, CODE_RUN_BUCKET);
        if (!decision.allowed()) {
            throw new RateLimitedException("Too many code runs; wait a moment", Math.max(1, decision.retryAfterMillis() / 1000));
        }
        InterviewSession session = ownedReadOnly(userId, sessionId);
        if (session.getMode() != InterviewMode.DSA) {
            throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "Code can only be run in coding rounds");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_TRANSITION, "The interview is not in progress");
        }
        SessionQuestion current = sessionQuestions.findBySessionIdAndPosition(sessionId, session.getCurrentIndex())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TRANSITION, "There is no current question"));
        if (current.getQuestionId() == null) {
            throw new AppException(ErrorCode.UNSUPPORTED_FOR_MODE, "This question has no tests");
        }
        Question bank = questions.require(current.getQuestionId());
        RunResult result = codeRunner.run(questions.dsa(bank), request.language(), request.source(), submit);
        String kind = submit ? "SUBMIT" : "RUN";
        List<TestResultResponse> tests = result.tests().stream().map(t -> t.sample()
                ? new TestResultResponse(t.index(), true, t.verdict().name(), t.input(), t.expected(), t.actual(),
                        t.stdout(), t.stderr(), t.timeMs())
                : new TestResultResponse(t.index(), false, t.verdict().name(), null, null, null, null, null, t.timeMs()))
                .toList();
        UUID submissionId = jdbc.insertCodeSubmission(current.getId(), userId, kind, request.language().name(),
                request.source(), result.verdict().name(), result.passed(), result.total(), write(tests),
                result.runtimeMs(), result.executor());
        CodeRunResponse response = new CodeRunResponse(submissionId, kind, result.verdict().name(), result.passed(),
                result.total(), result.runtimeMs(), result.executor(), result.executed(), result.compileOutput(), tests);
        if (submit) {
            String summary = result.executed()
                    ? "Submitted " + request.language().name().toLowerCase(Locale.ROOT) + " solution: " + result.verdict()
                            + " (" + result.passed() + "/" + result.total() + " tests passed)"
                    : "Submitted " + request.language().name().toLowerCase(Locale.ROOT)
                            + " solution (code execution is disabled on this server; tests were not run)";
            ObjectNode metadata = json.createObjectNode().put("verdict", result.verdict().name())
                    .put("passed", result.passed()).put("total", result.total()).put("executed", result.executed())
                    .put("language", request.language().name()).put("submissionId", submissionId.toString());
            engine.dispatch(sessionId, userId, new SessionCommand.CodeRan(summary, metadata));
            stream.publish(sessionId, "code.result", response);
        }
        return response;
    }

    // ------------------------------------------------------------------ report

    @Transactional(readOnly = true)
    public ReportResponse report(UUID userId, UUID sessionId) {
        InterviewSession session = owned(userId, sessionId);
        if (session.getStatus() == SessionStatus.ABANDONED) {
            throw new AppException(ErrorCode.CONFLICT, "Abandoned interviews have no report");
        }
        if (session.getStatus() != SessionStatus.EVALUATING && session.getStatus() != SessionStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONFLICT, "The interview has not finished yet");
        }
        Evaluation evaluation = evaluations.findBySessionId(sessionId).orElse(null);
        List<ShareLinkResponse> links = shareLinks.forSession(sessionId).stream()
                .map(l -> shareView(l, null)).toList();
        return new ReportResponse(summary(session, evaluation), evaluationView(session, evaluation),
                turns.findBySessionIdOrderBySeq(sessionId).stream().map(InterviewService::turn).toList(), links);
    }

    public void retryEvaluation(UUID userId, UUID sessionId) {
        boolean submit = Boolean.TRUE.equals(tx.execute(status -> {
            InterviewSession session = owned(userId, sessionId);
            Evaluation evaluation = evaluations.findBySessionId(sessionId).orElse(null);
            if (session.getStatus() != SessionStatus.EVALUATING || evaluation == null
                    || evaluation.getStatus() != EvaluationStatus.FAILED) {
                throw new AppException(ErrorCode.CONFLICT, "Only a failed evaluation can be retried");
            }
            evaluation.reset();
            evaluations.save(evaluation);
            return true;
        }));
        if (submit) {
            stream.publish(sessionId, "evaluation.progress", Map.of("status", "PENDING"));
            effects.submit(sessionId, new Effect.Evaluate());
        }
    }

    private EvaluationResponse evaluationView(InterviewSession session, Evaluation e) {
        if (e == null) {
            return new EvaluationResponse("PENDING", 0, null, null, null, null, List.of(), null, null, null, null, null,
                    null, null, null);
        }
        Rubric rubric = rubrics.require(e.getRubricId());
        RubricRef rubricRef = new RubricRef(rubric.getKey(), rubric.getVersion());
        if (e.getStatus() != EvaluationStatus.SUCCEEDED) {
            return new EvaluationResponse(e.getStatus().name(), e.getAttempts(), e.getError(), null, null, null, List.of(),
                    null, null, null, null, null, rubricRef, null, null);
        }
        Map<String, String> labels = labels(rubric);
        List<CompetencyResponse> competencies = jdbc.competencyScores(e.getId()).stream()
                .map(c -> competency(c, labels)).toList();
        return new EvaluationResponse(e.getStatus().name(), e.getAttempts(), null,
                e.getOverallScore() == null ? null : e.getOverallScore().doubleValue(), e.getConfidence(), e.getSummary(),
                competencies, e.getStrengths(), e.getGaps(), e.getNextSteps(), e.getPerQuestion(), e.getIntegrity(),
                rubricRef, new EvaluatorRef(e.getProvider(), e.getModel(), e.getPromptVersion()), e.getCompletedAt());
    }

    private CompetencyResponse competency(CompetencyScoreRow c, Map<String, String> labels) {
        JsonNode evidence = read(c.evidenceJson());
        List<EvidenceResponse> quotes = new ArrayList<>();
        evidence.path("quotes").forEach(q -> quotes.add(new EvidenceResponse(q.path("quote").asText(),
                q.path("turnSeq").asInt())));
        List<String> adjustments = new ArrayList<>();
        evidence.path("adjustments").forEach(a -> adjustments.add(a.asText()));
        return new CompetencyResponse(c.competency(), labels.getOrDefault(c.competency(), c.competency()), c.score(),
                c.weight(), c.lowConfidence(), c.rationale(), quotes, adjustments);
    }

    private Map<String, String> labels(Rubric rubric) {
        RubricDefinition definition = rubrics.definition(rubric);
        Map<String, String> labels = new HashMap<>();
        definition.competencies().forEach(c -> labels.put(c.competency().name(), c.label()));
        return labels;
    }

    // ------------------------------------------------------------------ sharing

    @Transactional
    public ShareLinkResponse share(UUID userId, UUID sessionId, int expiresInDays) {
        InterviewSession session = owned(userId, sessionId);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONFLICT, "Only completed interviews can be shared");
        }
        String token = Hashing.randomToken(24);
        ShareLink link = shareLinks.insert(sessionId, userId, Hashing.sha256Hex(token),
                clock.instant().plus(Duration.ofDays(expiresInDays)));
        return shareView(link, app.web().baseUrl() + "/r/" + token);
    }

    @Transactional
    public void revokeShares(UUID userId, UUID sessionId) {
        owned(userId, sessionId);
        shareLinks.revokeAll(sessionId, clock.instant());
    }

    /** Anonymised report for a valid share token; 404 for unknown, expired or revoked tokens alike. */
    @Transactional
    public PublicReportResponse publicReport(String token) {
        if (token == null || token.length() > 100) {
            throw new AppException(ErrorCode.NOT_FOUND, "Report not found");
        }
        UUID sessionId = shareLinks.resolve(Hashing.sha256Hex(token), clock.instant())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Report not found"));
        InterviewSession session = sessions.findById(sessionId).orElseThrow();
        Evaluation e = evaluations.findBySessionId(sessionId)
                .filter(ev -> ev.getStatus() == EvaluationStatus.SUCCEEDED)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Report not found"));
        String name = users.getActive(session.getUserId()).getName();
        String firstName = name == null || name.isBlank() ? "A candidate" : name.trim().split("\\s+")[0];
        Map<String, String> labels = labels(rubrics.require(e.getRubricId()));
        List<PublicCompetency> competencies = jdbc.competencyScores(e.getId()).stream()
                .map(c -> new PublicCompetency(c.competency(), labels.getOrDefault(c.competency(), c.competency()),
                        c.score(), c.lowConfidence()))
                .toList();
        List<PublicQuestion> qs = new ArrayList<>();
        if (e.getPerQuestion() != null) {
            e.getPerQuestion().forEach(q -> qs.add(new PublicQuestion(q.path("position").asInt(), q.path("title").asText(),
                    q.path("difficulty").asText(), q.hasNonNull("turnScore") ? q.get("turnScore").asDouble() : null)));
        }
        return new PublicReportResponse(firstName, session.getMode(), session.getTargetRole(), session.getCompany(),
                e.getCompletedAt(), e.getOverallScore() == null ? null : e.getOverallScore().doubleValue(),
                e.getConfidence(), e.getSummary(), competencies, e.getStrengths(), e.getGaps(), qs);
    }

    private static ShareLinkResponse shareView(ShareLink l, String url) {
        return new ShareLinkResponse(l.id(), url, l.expiresAt(), l.revokedAt(), l.viewCount(), l.createdAt());
    }

    // ------------------------------------------------------------------ mapping helpers

    private InterviewSession owned(UUID userId, UUID sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Interview not found"));
        if (!session.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Interview not found");
        }
        return session;
    }

    private InterviewSession ownedReadOnly(UUID userId, UUID sessionId) {
        return tx.execute(status -> owned(userId, sessionId));
    }

    static SessionSummaryResponse summary(InterviewSession s, Evaluation e) {
        Double score = e != null && e.getStatus() == EvaluationStatus.SUCCEEDED && e.getOverallScore() != null
                ? e.getOverallScore().doubleValue() : null;
        return new SessionSummaryResponse(s.getId(), s.getMode(), s.getStatus(), s.getStage(), s.isProcessing(),
                s.getTargetRole(), s.getCompany(), s.getPackSlug(), s.getPlannedQuestions(), s.getCurrentIndex(),
                s.getCurrentDifficulty(), s.isVoiceEnabled(), s.getEndReason(), score, s.getCreatedAt(), s.getStartedAt(),
                s.getEndedAt());
    }

    static SessionQuestionResponse question(SessionQuestion q) {
        return new SessionQuestionResponse(q.getId(), q.getPosition(), q.getTitle(), q.getPromptMd(), q.getDifficulty(),
                q.getDifficultyReason(), q.getTags(), q.getContext(), q.getHintsUsed(), q.getFollowUps(),
                q.getTurnScore() == null ? null : q.getTurnScore().doubleValue(), q.getCompletedAt() != null);
    }

    static TurnResponse turn(Turn t) {
        return new TurnResponse(t.getId(), t.getSeq(), t.getRole().name(), t.getKind().name(), t.getContent(),
                t.getFormat().name(), t.getInputMode().name(), t.getSessionQuestionId(), t.getMetadata(), t.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String value) {
        try {
            return value == null ? json.createObjectNode() : json.readTree(value);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
