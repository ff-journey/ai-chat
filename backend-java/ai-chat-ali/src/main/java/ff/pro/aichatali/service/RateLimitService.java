package ff.pro.aichatali.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
public class RateLimitService {

    @Value("${rate-limit.window-seconds:7200}")
    private int windowSeconds;

    @Value("${rate-limit.max-rounds:50}")
    private int maxRounds;

    private final ConcurrentHashMap<Long, Deque<Instant>> userRequests = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean allowed, int used, int limit) {}

    public RateLimitResult checkAndRecord(long userId) {
        Deque<Instant> timestamps = userRequests.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);

        // Remove expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();
        }

        int used = timestamps.size();
        if (used >= maxRounds) {
            return new RateLimitResult(false, used, maxRounds);
        }

        timestamps.addLast(Instant.now());
        return new RateLimitResult(true, used + 1, maxRounds);
    }

    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        userRequests.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
        log.debug("Rate limit cleanup done, active users: {}", userRequests.size());
    }
}
