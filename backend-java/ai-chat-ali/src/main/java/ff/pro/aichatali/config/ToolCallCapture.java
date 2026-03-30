package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import ff.pro.aichatali.common.SpanContext;
import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.tool.PluggableTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ToolCallCapture extends ToolInterceptor {

    private final SseService sseService;
    private final Set<String> agentToolNames;

    public ToolCallCapture(SseService sseService, List<PluggableTool> tools) {
        this.sseService = sseService;
        this.agentToolNames = tools.stream()
                .filter(PluggableTool::isAgent)
                .map(PluggableTool::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String threadId = (String) request.getContext().get(SysContext.THREAD_ID);
        String parentSpanId = (String) request.getContext().get(SysContext.CURRENT_SPAN_ID);
        String toolName = request.getToolName();

        // Agent-type tools manage their own spans in apply()
        if (agentToolNames.contains(toolName)) {
            return handler.call(request);
        }

        SpanContext span = SpanContext.create(SpanContext.SpanType.TOOL, toolName, parentSpanId);
        if (threadId != null) {
            sseService.push(threadId, TraceEvent.spanStart(span));
        }

        ToolCallResponse response;
        try {
            log.debug("Tool {} called: {}", toolName, request.getArguments());
            response = handler.call(request);
            log.debug("Tool {} result: {}", toolName, response.getStatus());
        } catch (Exception e) {
            log.error("Tool {} failed: {}", toolName, e.getMessage(), e);
            if (threadId != null) {
                sseService.push(threadId, TraceEvent.spanEnd(span, "error", e.getMessage()));
            }
            return ToolCallResponse.of(request.getToolCallId(), toolName, "工具执行失败: " + e.getMessage());
        }

        if (threadId != null) {
            String result = response.getResult();
            String output = result != null && result.length() > 500
                    ? result.substring(0, 500) + "..." : result;
            sseService.push(threadId, TraceEvent.spanEnd(span, "ok", output));
        }

        return response;
    }

    @Override
    public String getName() {
        return "ToolCallCapture";
    }
}
