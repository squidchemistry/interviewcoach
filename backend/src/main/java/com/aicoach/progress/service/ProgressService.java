package com.aicoach.progress.service;

import com.aicoach.ai.repository.AiCallLogRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository;
import com.aicoach.interview.repository.InterviewJdbcRepository.TopicStat;
import com.aicoach.interview.service.InterviewProperties;
import com.aicoach.interview.service.InterviewService;
import com.aicoach.progress.dto.ProgressDtos.ModeSummary;
import com.aicoach.progress.dto.ProgressDtos.ProgressResponse;
import com.aicoach.progress.dto.ProgressDtos.RevisionItem;
import com.aicoach.progress.dto.ProgressDtos.RevisionResponse;
import com.aicoach.progress.dto.ProgressDtos.SessionScore;
import com.aicoach.progress.dto.ProgressDtos.SuggestedQuestion;
import com.aicoach.progress.dto.ProgressDtos.TopicResponse;
import com.aicoach.progress.dto.ProgressDtos.Totals;
import com.aicoach.progress.dto.ProgressDtos.TrendPoint;
import com.aicoach.progress.dto.ProgressDtos.UsageResponse;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.service.QuestionService;
import com.aicoach.user.domain.Plan;
import com.aicoach.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for the dashboard, the usage meter and the spaced-repetition revision queue. */
@Service
public class ProgressService {

    static final int TREND_SESSIONS = 20;
    static final int SUGGESTIONS_PER_TOPIC = 3;

    private final InterviewJdbcRepository jdbc;
    private final AiCallLogRepository aiCalls;
    private final InterviewService interviews;
    private final QuestionService questions;
    private final UserService users;
    private final InterviewProperties properties;
    private final Clock clock;

    public ProgressService(InterviewJdbcRepository jdbc, AiCallLogRepository aiCalls, InterviewService interviews,
            QuestionService questions, UserService users, InterviewProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.aiCalls = aiCalls;
        this.interviews = interviews;
        this.questions = questions;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(UUID userId) {
        Plan plan = users.getActive(userId).getPlan();
        Instant now = clock.instant();
        String period = YearMonth.from(now.atZone(ZoneOffset.UTC)).toString();
        return new UsageResponse(period, plan.name(), jdbc.interviewsStarted(userId, period),
                plan == Plan.PRO ? null : properties.freeMonthlyInterviews(),
                aiCalls.tokensForUserSince(userId, now.truncatedTo(ChronoUnit.DAYS)));
    }

    @Transactional(readOnly = true)
    public ProgressResponse progress(UUID userId, String mode) {
        Map<String, Object> t = jdbc.totals(userId);
        Totals totals = new Totals(number(t.get("sessions")), number(t.get("completed")), decimal(t.get("avg_overall")),
                decimal(t.get("best_overall")), number(t.get("answered")));
        List<ModeSummary> modes = jdbc.modeSummaries(userId).stream()
                .map(m -> new ModeSummary(m.mode(), m.count(), round(m.avgOverall()))).toList();
        List<SessionScore> scores = jdbc.sessionScores(userId, mode, TREND_SESSIONS).stream()
                .map(s -> new SessionScore(s.sessionId(), s.mode(), s.overallScore(), s.at())).toList();
        List<TrendPoint> trends = jdbc.trends(userId, mode, TREND_SESSIONS).stream()
                .map(p -> new TrendPoint(p.sessionId(), p.mode(), p.competency(), p.score(), p.at())).toList();
        List<TopicResponse> weak = jdbc.weakTopics(userId, 8).stream().map(ProgressService::topic).toList();
        return new ProgressResponse(totals, modes, scores, trends, weak, interviews.recent(userId, 5));
    }

    /** Topics ordered due-first (SM-2), each with up to three published questions to practise. */
    @Transactional(readOnly = true)
    public RevisionResponse revisionQueue(UUID userId) {
        Instant now = clock.instant();
        List<Question> pool = new ArrayList<>();
        for (QuestionType type : QuestionType.values()) {
            pool.addAll(questions.published(type));
        }
        List<RevisionItem> items = new ArrayList<>();
        for (TopicStat s : jdbc.dueTopics(userId, now)) {
            List<SuggestedQuestion> suggestions = pool.stream()
                    .filter(q -> q.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).equals(s.tag())))
                    .sorted(Comparator.comparing((Question q) -> q.getDifficulty().ordinal()).thenComparing(Question::getSlug))
                    .limit(SUGGESTIONS_PER_TOPIC)
                    .map(q -> new SuggestedQuestion(q.getId(), q.getSlug(), q.getTitle(), q.getType().name(),
                            q.getDifficulty().name()))
                    .toList();
            items.add(new RevisionItem(s.tag(), s.dueAt(), !s.dueAt().isAfter(now), s.avgScore(), s.attempts(),
                    s.intervalDays(), suggestions));
        }
        return new RevisionResponse(items);
    }

    private static TopicResponse topic(TopicStat s) {
        return new TopicResponse(s.tag(), s.attempts(), s.avgScore(), s.lastScore(), s.dueAt(), s.intervalDays());
    }

    private static int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static Double decimal(Object value) {
        return value == null ? null : round(((Number) value).doubleValue());
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
