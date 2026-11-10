package com.aicoach.interview.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-session event log on Redis Streams, the backbone of SSE streaming.
 * <ul>
 *   <li>Producers (any API replica) {@code XADD} events; the entry id is the SSE event id.</li>
 *   <li>SSE handlers (any replica) read entries after the client's {@code Last-Event-ID}, so a reconnecting client
 *       replays exactly what it missed.</li>
 *   <li>A local monitor wakes same-process readers immediately; cross-replica readers poll at a short interval.</li>
 * </ul>
 * Streams are capped (~2000 entries) and expire 24 h after the last write.
 */
@Component
public class SessionEventStream {

    private static final Logger log = LoggerFactory.getLogger(SessionEventStream.class);
    private static final long MAX_LEN = 2_000;
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Map<UUID, Object> monitors = new ConcurrentHashMap<>();

    public SessionEventStream(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public record Event(String id, String type, String data) {
    }

    static String key(UUID sessionId) {
        return "stream:session:" + sessionId;
    }

    /** Appends an event; returns its id. Failures are logged, never thrown (clients recover via GET + resync). */
    public String publish(UUID sessionId, String type, Object payload) {
        try {
            String data = json.writeValueAsString(payload);
            RecordId id = redis.opsForStream().add(StreamRecords.newRecord().in(key(sessionId))
                    .ofMap(Map.of("type", type, "data", data)));
            redis.opsForStream().trim(key(sessionId), MAX_LEN, true);
            redis.expire(key(sessionId), TTL);
            signal(sessionId);
            return id == null ? null : id.getValue();
        } catch (Exception e) {
            log.warn("Could not publish {} event for session {}: {}", type, sessionId, e.toString());
            return null;
        }
    }

    /** Events strictly after {@code afterId} ("0-0" = from the beginning). */
    public List<Event> readAfter(UUID sessionId, String afterId, int count) {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                StreamReadOptions.empty().count(count), StreamOffset.create(key(sessionId), ReadOffset.from(afterId)));
        List<Event> events = new ArrayList<>();
        if (records != null) {
            for (MapRecord<String, Object, Object> record : records) {
                events.add(new Event(record.getId().getValue(), String.valueOf(record.getValue().get("type")),
                        String.valueOf(record.getValue().get("data"))));
            }
        }
        return events;
    }

    /** Id of the newest event, or "0-0" if there are none. */
    public String lastId(UUID sessionId) {
        try {
            var latest = redis.opsForStream().reverseRange(key(sessionId),
                    org.springframework.data.domain.Range.unbounded(),
                    org.springframework.data.redis.connection.Limit.limit().count(1));
            return latest == null || latest.isEmpty() ? "0-0" : latest.getFirst().getId().getValue();
        } catch (Exception e) {
            return "0-0";
        }
    }

    /** Id of the oldest retained event, or null if the stream is empty or missing. */
    public String firstId(UUID sessionId) {
        try {
            var first = redis.opsForStream().range(key(sessionId), org.springframework.data.domain.Range.unbounded(),
                    org.springframework.data.redis.connection.Limit.limit().count(1));
            return first == null || first.isEmpty() ? null : first.getFirst().getId().getValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** Compares two stream ids ("ms-seq"). */
    public static int compareIds(String a, String b) {
        String[] x = a.split("-");
        String[] y = b.split("-");
        int c = Long.compare(Long.parseLong(x[0]), Long.parseLong(y[0]));
        return c != 0 ? c : Long.compare(Long.parseLong(x.length > 1 ? x[1] : "0"), Long.parseLong(y.length > 1 ? y[1] : "0"));
    }

    /** Blocks up to {@code timeout} or until a local publish for this session. */
    public void awaitSignal(UUID sessionId, Duration timeout) throws InterruptedException {
        Object monitor = monitors.computeIfAbsent(sessionId, id -> new Object());
        synchronized (monitor) {
            monitor.wait(timeout.toMillis());
        }
    }

    private void signal(UUID sessionId) {
        Object monitor = monitors.get(sessionId);
        if (monitor != null) {
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
    }
}
