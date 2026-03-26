package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import ff.pro.aichatali.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Session management endpoints.
 * threadId convention: userId + "_" + sessionId  (or just sessionId when userId is "default").
 */
@RestController
@RequestMapping("/sessions")
@Slf4j
public class SessionController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    @Qualifier("supervisor_agent")
    private ReactAgent supervisorAgent;

    /**
     * GET /sessions/{userId}
     * Returns: { "sessions": [ { "sessionId", "title", "messageCount", "updatedAt" } ] }
     * sessionId is the bare session part (without the userId_ prefix).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> listSessions(@PathVariable String userId) {
        String prefix = "default".equals(userId) ? null : userId + "_";

        List<Map<String, Object>> sessions = chatHistoryService.getAllThreadInfos().stream()
                .filter(info -> {
                    if (prefix == null) return true;
                    return info.threadId().startsWith(prefix);
                })
                .map(info -> {
                    // Strip userId_ prefix to get bare sessionId
                    String sessionId = (prefix != null && info.threadId().startsWith(prefix))
                            ? info.threadId().substring(prefix.length())
                            : info.threadId();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sessionId", sessionId);
                    m.put("title", info.title().isBlank() ? sessionId : info.title());
                    m.put("messageCount", info.messageCount());
                    m.put("updatedAt", info.updatedAt());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("sessions", sessions));
    }

    /**
     * GET /sessions/{userId}/{sessionId}
     * Returns: { "messages": [ { "messageType": "user"|"assistant", "content": "...", "imageUrl": "..." } ] }
     */
    @GetMapping("/{userId}/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String userId,
            @PathVariable String sessionId) {

        String threadId = resolveThreadId(userId, sessionId);
        List<Map<String, Object>> messages = new ArrayList<>();

        // Pre-collect imageUrls from ChatHistoryService (order matches user messages)
        List<String> imageUrls = chatHistoryService.getHistory(threadId).stream()
                .filter(r -> "user".equals(r.role()))
                .map(ChatHistoryService.MessageRecord::imageUrl)
                .toList();

        try {
            CompiledGraph compiledGraph = supervisorAgent.getCompiledGraph();
            if (compiledGraph != null) {
                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
                Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(config);
                if (snapshotOpt.isPresent()) {
                    OverAllState state = snapshotOpt.get().state();
                    Object messagesObj = state.data().get("messages");
                    if (messagesObj instanceof List<?> rawMessages) {
                        int userIdx = 0;
                        for (Object rawMsg : rawMessages) {
                            if (rawMsg instanceof UserMessage userMsg) {
                                Map<String, Object> entry = new LinkedHashMap<>();
                                entry.put("messageType", "user");
                                entry.put("content", userMsg.getText() != null ? userMsg.getText() : "");
                                if (userIdx < imageUrls.size() && imageUrls.get(userIdx) != null) {
                                    entry.put("imageUrl", imageUrls.get(userIdx));
                                }
                                messages.add(entry);
                                userIdx++;
                            } else if (rawMsg instanceof AssistantMessage assistantMsg) {
                                if (!assistantMsg.hasToolCalls()) {
                                    String text = assistantMsg.getText();
                                    if (text != null && !text.isEmpty()) {
                                        Map<String, Object> entry = new LinkedHashMap<>();
                                        entry.put("messageType", "assistant");
                                        entry.put("content", text);
                                        messages.add(entry);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MemorySaver lookup failed for thread {}: {}", threadId, e.getMessage());
        }

        // Fallback: ChatHistoryService
        if (messages.isEmpty()) {
            chatHistoryService.getHistory(threadId).forEach(record -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("messageType", "user".equals(record.role()) ? "user" : "assistant");
                entry.put("content", record.content());
                if ("user".equals(record.role()) && record.imageUrl() != null) {
                    entry.put("imageUrl", record.imageUrl());
                }
                messages.add(entry);
            });
        }

        return ResponseEntity.ok(Map.of("messages", messages));
    }

    /**
     * PUT /sessions/{userId}/{sessionId}/title
     * Body: { "title": "..." }
     * Sets the human-readable title for the session (called by frontend after LLM summary).
     */
    @PutMapping("/{userId}/{sessionId}/title")
    public ResponseEntity<Void> setTitle(
            @PathVariable String userId,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title != null && !title.isBlank()) {
            chatHistoryService.setTitle(resolveThreadId(userId, sessionId), title);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /sessions/{userId}/{sessionId}
     */
    @DeleteMapping("/{userId}/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String userId,
            @PathVariable String sessionId) {
        chatHistoryService.deleteThread(resolveThreadId(userId, sessionId));
        return ResponseEntity.noContent().build();
    }

    private String resolveThreadId(String userId, String sessionId) {
        return "default".equals(userId) ? sessionId : userId + "_" + sessionId;
    }
}
