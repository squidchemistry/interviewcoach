package com.aicoach.interview.service.round;

import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory: maps an interview mode to its round strategy (all strategies are Spring beans). */
@Component
public class RoundFactory {

    private final Map<InterviewMode, InterviewRound> rounds = new EnumMap<>(InterviewMode.class);

    public RoundFactory(List<InterviewRound> all) {
        all.forEach(round -> rounds.put(round.mode(), round));
        for (InterviewMode mode : InterviewMode.values()) {
            if (!rounds.containsKey(mode)) {
                throw new IllegalStateException("No interview round registered for " + mode);
            }
        }
    }

    public InterviewRound forMode(InterviewMode mode) {
        return rounds.get(mode);
    }
}
