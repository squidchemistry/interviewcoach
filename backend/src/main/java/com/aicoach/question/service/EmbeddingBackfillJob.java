package com.aicoach.question.service;

import com.aicoach.ai.service.AiGateway;
import com.aicoach.question.repository.QuestionEmbeddingRepository;
import com.aicoach.question.repository.QuestionEmbeddingRepository.PendingEmbedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps question embeddings current: embeds new or edited questions and re-embeds everything after the embedding
 * model changes. Runs at startup and every 30 s; also triggered by the admin "reindex" action. Idempotent and safe
 * to run on several replicas (the worst case is the same vector written twice).
 */
@Service
public class EmbeddingBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillJob.class);
    private static final int BATCH = 50;

    private final QuestionEmbeddingRepository embeddings;
    private final AiGateway ai;

    public EmbeddingBackfillJob(QuestionEmbeddingRepository embeddings, AiGateway ai) {
        this.embeddings = embeddings;
        this.ai = ai;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        embedPending();
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void scheduled() {
        embedPending();
    }

    /** Embeds all pending questions; returns how many were embedded. */
    public synchronized int embedPending() {
        int total = 0;
        String model = ai.embeddingModel();
        try {
            while (true) {
                var batch = embeddings.findPending(model, BATCH);
                if (batch.isEmpty()) {
                    break;
                }
                for (PendingEmbedding pending : batch) {
                    embeddings.save(pending.id(), ai.embed(pending.text()), model);
                    total++;
                }
                if (batch.size() < BATCH) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Embedding backfill stopped after {} questions: {}", total, e.toString());
        }
        if (total > 0) {
            log.info("Embedded {} questions with {}", total, model);
        }
        return total;
    }
}
