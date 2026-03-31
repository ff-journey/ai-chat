package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import ff.pro.aichatali.common.SpanContext;
import ff.pro.aichatali.common.SysContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingModelInterceptor extends ModelInterceptor {

    private final SseService sseService;

    @Override
    public String getName() {
        return "SimpleLLMLoggingInterceptor";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        long startTime = System.currentTimeMillis();

        String threadId = (String) request.getContext().get(SysContext.THREAD_ID);
        String parentSpanId = (String) request.getContext().get(SysContext.CURRENT_SPAN_ID);
        String parentNode = (String) request.getContext().get(SysContext.PARENT_NODE);
        String agentName = parentNode != null ? parentNode : "supervisor";

        SpanContext span = SpanContext.create(SpanContext.SpanType.LLM, agentName + "_llm", parentSpanId);

        Message last = request.getMessages().getLast();
        String inputPreview = messagePreview(last, 20);
        log.info("[SPAN] [{}] [llm:{}] call_start agent={} input={}",
                threadId, span.spanId(), agentName, inputPreview);
        if (threadId != null) {
            sseService.push(threadId, TraceEvent.spanStart(span));
        }

        // Filter out empty AssistantMessages that cause DeepSeek-V3 / SiliconFlow HTTP 400
        List<Message> filtered = request.getMessages().stream()
                .filter(m -> {
                    if (m instanceof AssistantMessage am) {
                        boolean emptyText = am.getText() == null || am.getText().isBlank();
                        boolean emptyTools = am.getToolCalls() == null || am.getToolCalls().isEmpty();
                        if (emptyText && emptyTools) {
                            log.warn("[SPAN] [{}] [llm:{}] ANOMALY filtered_empty_assistant",
                                    threadId, span.spanId());
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
        if (filtered.size() != request.getMessages().size()) {
            request = ModelRequest.builder(request).messages(filtered).build();
        }

        ModelResponse response = handler.call(request);

        if (response.getMessage() instanceof Flux) {
            AtomicReference<StringBuilder> buffer = new AtomicReference<>(new StringBuilder());
            Flux<ChatResponse> flux = (Flux<ChatResponse>) response.getMessage();
            boolean isSupervisor = "supervisor".equals(agentName);
            ModelRequest finalRequest = request;
            Flux<ChatResponse> wrapped = flux.doOnNext(chunk -> {
                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                    String text = chunk.getResult().getOutput().getText();
                    if (text != null) {
                        buffer.get().append(text);
                        if (!isSupervisor && threadId != null && !text.isEmpty()) {
                            sseService.push(threadId, TraceEvent.token(text, parentSpanId));
                        }
                    }
                }
            }).doOnError(e -> {
                log.error("[SPAN] [{}] [llm:{}] call_error agent={} duration={}ms",
                        threadId, span.spanId(), agentName,
                        System.currentTimeMillis() - startTime, e);
                log.debug("[SPAN] [{}] [llm:{}] call_error messages={}", threadId, span.spanId(),
                        finalRequest.getMessages().toString());
                if (threadId != null) {
                    sseService.push(threadId, TraceEvent.spanEnd(span, "error", e.getMessage()));
                }
            }).doOnComplete(() -> {
                String fullText = buffer.get().toString();
                log.info("[SPAN] [{}] [llm:{}] call_end agent={} duration={}ms len={}",
                        threadId, span.spanId(), agentName,
                        System.currentTimeMillis() - startTime, fullText.length());
                if (threadId != null) {
                    sseService.push(threadId, TraceEvent.spanEnd(span, "ok", null));
                }
            });
            return ModelResponse.of(wrapped);
        } else {
            AssistantMessage msg = (AssistantMessage) response.getMessage();
            String text = msg.getText();
            log.info("[SPAN] [{}] [llm:{}] call_end agent={} duration={}ms len={}",
                    threadId, span.spanId(), agentName,
                    System.currentTimeMillis() - startTime,
                    text != null ? text.length() : 0);
            return response;
        }
    }

    private String messagePreview(Message m, int maxLen) {
        String text;
        if (m instanceof ToolResponseMessage trm) {
            text = trm.getResponses().toString();
        } else if (m instanceof AssistantMessage am) {
            text = "text=" + am.getText() + ", toolCalls=" + am.getToolCalls();
        } else {
            text = m.getText();
        }
        if (text != null && text.length() > maxLen) {
            text = text.substring(0, maxLen) + "...";
        }
        return m.getClass().getSimpleName() + ": " + text;
    }
}
