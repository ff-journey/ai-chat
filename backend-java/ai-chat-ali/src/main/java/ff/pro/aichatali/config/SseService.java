package ff.pro.aichatali.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseService {

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter connect(String threadId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(threadId));
        emitter.onTimeout(() -> emitters.remove(threadId));
        emitter.onError(e -> emitters.remove(threadId));
        emitters.put(threadId, emitter);
        log.debug("SSE connected: {}", threadId);
        return emitter;
    }

    public void push(String threadId, AgentEvent event) {
        if (threadId == null) return;
        SseEmitter emitter = emitters.get(threadId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            log.debug("SSE push failed for {}: {}", threadId, e.getMessage());
            emitters.remove(threadId);
        }
    }

    public void complete(String threadId) {
        SseEmitter emitter = emitters.remove(threadId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }
}
