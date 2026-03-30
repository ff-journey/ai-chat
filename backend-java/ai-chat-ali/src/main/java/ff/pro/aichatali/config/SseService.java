package ff.pro.aichatali.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseService {

    private final ConcurrentHashMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public void createSink(String threadId) {
        Sinks.Many<String> old = sinks.put(threadId, Sinks.many().unicast().onBackpressureBuffer());
        if (old != null) {
            old.tryEmitComplete();
        }
        log.debug("SSE sink created: {}", threadId);
    }

    public Flux<String> getFlux(String threadId) {
        Sinks.Many<String> sink = sinks.get(threadId);
        return sink != null ? sink.asFlux() : Flux.empty();
    }

    public void push(String threadId, Object event) {
        if (threadId == null) return;
        Sinks.Many<String> sink = sinks.get(threadId);
        if (sink == null) return;
        try {
            sink.tryEmitNext(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.debug("SSE push failed for {}: {}", threadId, e.getMessage());
        }
    }

    public void complete(String threadId) {
        Sinks.Many<String> sink = sinks.remove(threadId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
