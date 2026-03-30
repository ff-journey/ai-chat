package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import ff.pro.aichatali.common.SysContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        long startTime = System.currentTimeMillis();

        String threadId = (String) request.getContext().get(SysContext.THREAD_ID);
        String parentNode = (String) request.getContext().get(SysContext.PARENT_NODE);
        String agentName = parentNode != null ? "ragAgent" : "supervisorAgent";

        Message last = request.getMessages().getLast();
        String inputPreview = last.getText();
        if (inputPreview != null && inputPreview.length() > 200) {
            inputPreview = inputPreview.substring(0, 200) + "...";
        }
        log.info("=== LLM Call Start [{}] Last Input: {}", agentName, inputPreview);

        if (threadId != null) {
            sseService.push(threadId, AgentEvent.thinking(agentName, inputPreview, parentNode));
        }

        // Filter out empty AssistantMessages that cause DeepSeek-V3 / SiliconFlow HTTP 400
        List<Message> filtered = request.getMessages().stream()
                .filter(m -> {
                    if (m instanceof AssistantMessage am) {
                        boolean emptyText = am.getText() == null || am.getText().isBlank();
                        boolean emptyTools = am.getToolCalls() == null || am.getToolCalls().isEmpty();
                        if (emptyText && emptyTools) {
                            log.warn("Filtered empty AssistantMessage from conversation history");
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
            Flux<ChatResponse> wrapped = flux.doOnNext(chunk -> {
                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                    String text = chunk.getResult().getOutput().getText();
                    if (text != null) buffer.get().append(text);
                }
            }).doOnComplete(() -> {
                String out = buffer.get().toString();
                log.info("=== LLM Call End [{}] ({}ms)", agentName, System.currentTimeMillis() - startTime);
                if (threadId != null) {
                    String outPreview = out.length() > 200 ? out.substring(0, 200) + "..." : out;
                    sseService.push(threadId, AgentEvent.stepEnd(agentName, outPreview, parentNode));
                }
            });
            return ModelResponse.of(wrapped);
        } else {
            AssistantMessage msg = (AssistantMessage) response.getMessage();
            String out = msg.getText();
            log.info("=== LLM Call End [{}] ({}ms)", agentName, System.currentTimeMillis() - startTime);
            if (threadId != null) {
                String outPreview = out != null && out.length() > 200 ? out.substring(0, 200) + "..." : out;
                sseService.push(threadId, AgentEvent.stepEnd(agentName, outPreview, parentNode));
            }
            return response;
        }
    }
}
