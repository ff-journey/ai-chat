package ff.pro.aichatali.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory chat history storage, keyed by threadId.
 */
@Service
public class ChatHistoryService {

    private final Map<String, List<MessageRecord>> history = new ConcurrentHashMap<>();
    private final Map<String, String> titles = new ConcurrentHashMap<>();

    public void setTitle(String threadId, String title) {
        if (title != null && !title.isBlank()) titles.put(threadId, title);
    }

    public String getTitle(String threadId) {
        return titles.get(threadId);
    }

    public record ThreadInfo(String threadId, String title, int messageCount, String updatedAt) {}

    public List<ThreadInfo> getAllThreadInfos() {
        return history.entrySet().stream()
                .map(e -> {
                    List<MessageRecord> msgs = e.getValue();
                    String updatedAt = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).timestamp();
                    String title = titles.getOrDefault(e.getKey(), "");
                    return new ThreadInfo(e.getKey(), title, msgs.size(), updatedAt);
                })
                .sorted(Comparator.comparing(ThreadInfo::updatedAt).reversed())
                .collect(Collectors.toList());
    }

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

    public void deleteThread(String threadId) {
        history.remove(threadId);
    }

    public record MessageRecord(String role, String content, String timestamp, String imageUrl) {}

}
