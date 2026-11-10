package com.aicoach.interview.service.round;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.repository.SessionQuestionRepository;
import com.aicoach.question.domain.Question;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import com.aicoach.question.domain.QuestionEnums.QuestionType;
import com.aicoach.question.service.QuestionService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Picks the next bank question. Candidates are published questions of the round's type, restricted to the pack if one
 * was chosen, excluding questions already asked in this session and (if possible) those the user saw in the last
 * 30 days. Preference order relaxes step by step: difficulty + company → difficulty → any. The choice is seeded by
 * the session, so a session is reproducible.
 */
@Component
public class QuestionSelector {

    private final QuestionService questions;
    private final SessionQuestionRepository sessionQuestions;

    public QuestionSelector(QuestionService questions, SessionQuestionRepository sessionQuestions) {
        this.questions = questions;
        this.sessionQuestions = sessionQuestions;
    }

    public Question select(QuestionType type, Difficulty difficulty, InterviewSession session, Set<UUID> usedInSession,
            int position) {
        List<Question> pool = questions.published(type);
        if (session.getPackSlug() != null) {
            Set<String> slugs = new HashSet<>(questions.pack(session.getPackSlug()).getQuestionSlugs());
            pool = pool.stream().filter(q -> slugs.contains(q.getSlug())).toList();
        }
        pool = pool.stream().filter(q -> !usedInSession.contains(q.getId()))
                .sorted(Comparator.comparing(Question::getSlug)).toList();
        if (pool.isEmpty()) {
            throw new AppException(ErrorCode.NO_QUESTIONS_AVAILABLE, "No more " + type + " questions are available");
        }
        Set<UUID> recent = new HashSet<>(sessionQuestions.recentlyAskedQuestionIds(session.getUserId(),
                Instant.now().minus(Duration.ofDays(30))));
        String company = session.getCompany();
        List<Predicate<Question>> preferences = List.of(
                q -> q.getDifficulty() == difficulty && company != null && q.getCompanies().contains(company) && !recent.contains(q.getId()),
                q -> q.getDifficulty() == difficulty && !recent.contains(q.getId()),
                q -> q.getDifficulty() == difficulty,
                q -> !recent.contains(q.getId()),
                q -> true);
        Random random = new Random(session.getSeed() * 31 + position);
        for (Predicate<Question> preference : preferences) {
            List<Question> matches = pool.stream().filter(preference).toList();
            if (!matches.isEmpty()) {
                return matches.get(random.nextInt(matches.size()));
            }
        }
        return pool.getFirst();
    }
}
