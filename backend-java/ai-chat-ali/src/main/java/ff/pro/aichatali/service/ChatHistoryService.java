package ff.pro.aichatali.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory chat history storage, keyed by threadId.
 */
@Service
public class ChatHistoryService {

    private final Map<String, List<MessageRecord>> history = new ConcurrentHashMap<>();

    public void addMessage(String threadId, String role, String content) {
        addMessage(threadId, role, content, null);
    }

    public void addMessage(String threadId, String role, String content, String imageUrl) {
        history.computeIfAbsent(threadId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new MessageRecord(role, content, Instant.now().toString(), imageUrl));
    }

    public List<MessageRecord> getHistory(String threadId) {
        return history.getOrDefault(threadId, List.of());
    }

    public Set<String> getAllThreadIds() {
        return history.keySet();
    }

    public record MessageRecord(String role, String content, String timestamp, String imageUrl) {}

}
