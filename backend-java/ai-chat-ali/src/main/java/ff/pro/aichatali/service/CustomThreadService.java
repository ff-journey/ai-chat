package ff.pro.aichatali.service;

import com.alibaba.cloud.ai.agent.studio.dto.ListThreadsResponse;
import com.alibaba.cloud.ai.agent.studio.dto.Thread;
import com.alibaba.cloud.ai.agent.studio.service.ThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Custom ThreadService that overrides Studio's default in-memory implementation.
 * Adds integration with ChatHistoryService for message persistence.
 */
@Primary
@Service
public class CustomThreadService implements ThreadService {

    private static final Logger log = LoggerFactory.getLogger(CustomThreadService.class);

    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> threadStates = new ConcurrentHashMap<>();
    private final ChatHistoryService chatHistoryService;

    public CustomThreadService(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public Mono<Optional<Thread>> getThread(
            String appName, String userId, String threadId, Optional<Map<String, Object>> state) {
        return Mono.fromCallable(() -> {
            String key = buildKey(appName, userId, threadId);
            return Optional.ofNullable(threads.get(key));
        });
    }

    @Override
    public Mono<ListThreadsResponse> listThreads(String appName, String userId) {
        return Mono.fromCallable(() -> {
            String prefix = buildKeyPrefix(appName, userId);
            List<Thread> userThreads = threads.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            log.debug("Found {} threads for app={}, user={}", userThreads.size(), appName, userId);
            return ListThreadsResponse.of(userThreads);
        });
    }

    @Override
    public Mono<Thread> createThread(
            String appName, String userId, Map<String, Object> initialState, String threadId) {
        return Mono.fromCallable(() -> {
            String finalThreadId = (threadId == null || threadId.trim().isEmpty())
                    ? UUID.randomUUID().toString()
                    : threadId;
            String key = buildKey(appName, userId, finalThreadId);
            if (threads.containsKey(key)) {
                log.warn("Attempted to create duplicate thread: {}", finalThreadId);
                throw new IllegalStateException("Thread already exists: " + finalThreadId);
            }
            Thread newThread = Thread.builder(finalThreadId)
                    .appName(appName)
                    .userId(userId)
                    .build();
            threads.put(key, newThread);
            if (initialState != null && !initialState.isEmpty()) {
                threadStates.put(key, new ConcurrentHashMap<>(initialState));
            }
            log.info("Created thread: {} for app={}, user={}", finalThreadId, appName, userId);
            return newThread;
        });
    }

    @Override
    public Mono<Void> deleteThread(String appName, String userId, String threadId) {
        return Mono.fromRunnable(() -> {
            String key = buildKey(appName, userId, threadId);
            Thread removed = threads.remove(key);
            threadStates.remove(key);
            if (removed != null) {
                log.info("Deleted thread: {} for app={}, user={}", threadId, appName, userId);
            }
        });
    }

    public Map<String, Object> getThreadState(String appName, String userId, String threadId) {
        String key = buildKey(appName, userId, threadId);
        return threadStates.getOrDefault(key, new ConcurrentHashMap<>());
    }

    public void updateThreadState(
            String appName, String userId, String threadId, Map<String, Object> state) {
        String key = buildKey(appName, userId, threadId);
        if (threads.containsKey(key)) {
            threadStates.put(key, new ConcurrentHashMap<>(state));
        }
    }

    private String buildKey(String appName, String userId, String threadId) {
        return String.format("%s:%s:%s", appName, userId, threadId);
    }

    private String buildKeyPrefix(String appName, String userId) {
        return String.format("%s:%s:", appName, userId);
    }

}
