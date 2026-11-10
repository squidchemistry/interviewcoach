package com.aicoach.question.service;

import com.aicoach.ai.repository.AiCallLogRepository;
import com.aicoach.ai.AiUsageRow;
import com.aicoach.ai.service.AiGateway;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Admin read model over AI call metering. */
@Service
public class AiUsageService {

    private final AiCallLogRepository logs;
    private final AiGateway ai;

    public AiUsageService(AiCallLogRepository logs, AiGateway ai) {
        this.logs = logs;
        this.ai = ai;
    }

    public List<AiUsageRow> usage(String groupBy, Instant from, Instant to) {
        return logs.usage(groupBy, from, to);
    }

    public String embeddingModel() {
        return ai.embeddingModel();
    }
}
