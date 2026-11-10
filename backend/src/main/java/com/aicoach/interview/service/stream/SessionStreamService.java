package com.aicoach.interview.service.stream;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewSession;
import com.aicoach.interview.repository.SessionRepository;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Serves a session's event stream over SSE: replays events after the client's last id, then follows new ones.
 * Each connection runs on its own virtual thread; a heartbeat comment keeps proxies from closing idle streams.
 */
@Service
public class SessionStreamService {

    private static final Logger log = LoggerFactory.getLogger(SessionStreamService.class);
    private static final Pattern ID = Pattern.compile("^\\d{1,20}-\\d{1,10}$");
    static final Duration HEARTBEAT = Duration.ofSeconds(15);
    static final Duration POLL = Duration.ofSeconds(1);
    static final Duration MAX_CONNECTION = Duration.ofMinutes(30);

    private final SessionEventStream stream;
    private final SessionRepository sessions;
    private final ExecutorService executor;

    public SessionStreamService(SessionEventStream stream, SessionRepository sessions,
            @Qualifier("interviewExecutor") ExecutorService executor) {
        this.stream = stream;
        this.sessions = sessions;
        this.executor = executor;
    }

    public SseEmitter open(UUID userId, UUID sessionId, String lastEventId) {
        InterviewSession session = sessions.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Interview not found"));
        String after = lastEventId != null && ID.matcher(lastEventId).matches() ? lastEventId : null;
        SseEmitter emitter = new SseEmitter(MAX_CONNECTION.toMillis());
        boolean terminal = session.isTerminal();
        executor.submit(() -> pump(emitter, sessionId, after, terminal));
        return emitter;
    }

    private void pump(SseEmitter emitter, UUID sessionId, String lastEventId, boolean terminalAtStart) {
        long deadline = System.nanoTime() + MAX_CONNECTION.toNanos() - Duration.ofSeconds(5).toNanos();
        long lastWrite = System.nanoTime();
        try {
            String cursor = lastEventId;
            if (cursor == null) {
                cursor = "0-0";
            } else {
                String first = stream.firstId(sessionId);
                if (first != null && SessionEventStream.compareIds(first, cursor) > 0
                        && SessionEventStream.compareIds(cursor, "0-0") > 0) {
                    // the client's position was trimmed away: it must refetch the full state
                    emitter.send(SseEmitter.event().name("resync").data("{}", MediaType.APPLICATION_JSON));
                    cursor = stream.lastId(sessionId);
                }
            }
            boolean closing = terminalAtStart;
            while (System.nanoTime() < deadline) {
                List<SessionEventStream.Event> events = stream.readAfter(sessionId, cursor, 200);
                for (SessionEventStream.Event e : events) {
                    emitter.send(SseEmitter.event().id(e.id()).name(e.type()).data(e.data(), MediaType.APPLICATION_JSON));
                    cursor = e.id();
                    lastWrite = System.nanoTime();
                    if ("state.changed".equals(e.type())
                            && (e.data().contains("\"COMPLETED\"") || e.data().contains("\"ABANDONED\""))) {
                        closing = true;
                    }
                }
                if (closing && events.isEmpty()) {
                    break;
                }
                if (events.isEmpty()) {
                    if (System.nanoTime() - lastWrite >= HEARTBEAT.toNanos()) {
                        emitter.send(SseEmitter.event().comment("ping"));
                        lastWrite = System.nanoTime();
                    }
                    stream.awaitSignal(sessionId, POLL);
                }
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // client went away
            log.debug("SSE client for session {} disconnected: {}", sessionId, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException e) {
            log.warn("SSE stream for session {} failed", sessionId, e);
            emitter.completeWithError(e);
        }
    }
}
