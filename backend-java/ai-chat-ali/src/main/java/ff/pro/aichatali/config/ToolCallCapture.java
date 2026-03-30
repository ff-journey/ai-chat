package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import ff.pro.aichatali.common.SysContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * ToolInterceptor that pushes TOOL_START / TOOL_END events to the SSE observability channel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolCallCapture extends ToolInterceptor {

    private final SseService sseService;

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        AtomicReference<String> threadIdRef = new AtomicReference<>();
        AtomicReference<String> parentNodeRef = new AtomicReference<>();

        request.getExecutionContext().ifPresent(ctx -> {
            threadIdRef.set((String) ctx.config().metadata(SysContext.THREAD_ID).orElse(null));
            parentNodeRef.set((String) ctx.config().metadata(SysContext.PARENT_NODE).orElse(null));
        });

        String threadId = threadIdRef.get();
        String parentNode = parentNodeRef.get();

        if (threadId != null) {
            String argPreview = request.getArguments();
            if (argPreview != null && argPreview.length() > 300) {
                argPreview = argPreview.substring(0, 300) + "...";
            }
            sseService.push(threadId, AgentEvent.toolStart(request.getToolName(), argPreview, parentNode));
        }

        ToolCallResponse response;
        try {
            response = handler.call(request);
        } catch (Exception e) {
            log.error("工具 {} 执行异常: {}", request.getToolName(), e.getMessage(), e);
            String errorMsg = "工具执行失败: " + e.getMessage();
            if (threadId != null) {
                sseService.push(threadId, AgentEvent.toolEnd(request.getToolName(), errorMsg, parentNode));
            }
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), errorMsg);
        }

        if (threadId != null) {
            String result = response.getResult();
            if (result != null && result.length() > 500) {
                result = result.substring(0, 500) + "...";
            }
            sseService.push(threadId, AgentEvent.toolEnd(request.getToolName(), result, parentNode));
        }

        return response;
    }

    @Override
    public String getName() {
        return "ToolCallCapture";
    }
}
