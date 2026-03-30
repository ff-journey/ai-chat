package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.service.ToolRegistryService;
import ff.pro.aichatali.tool.PluggableTool;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DynamicToolPromptInterceptor extends ModelInterceptor {
    final ToolRegistryService toolRegistryService;

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<String> tools = request.getTools();
        if (CollectionUtils.isEmpty(tools)) {
            return handler.call(request);
        }
        if (CollectionUtils.isEmpty(request.getOptions().getToolCallbacks())) {
            return handler.call(request);
        }
        Object toolFlag = request.getContext().get(SysContext.TOOL_FLAG);
        if (toolFlag == null) {
            return handler.call(request);
        }
        request.getOptions().setToolCallbacks(toolRegistryService.dynamicCallbacks((int) toolFlag));

        // Inject threadId into toolContext so sub-tool BiFunction can read it via ToolContext.getContext()
        Object threadId = request.getContext().get(SysContext.THREAD_ID);
        if (threadId != null && request.getOptions() instanceof ToolCallingChatOptions opts) {
            Map<String, Object> tc = new HashMap<>(opts.getToolContext() != null ? opts.getToolContext() : Map.of());
            tc.put(SysContext.THREAD_ID, threadId);
            opts.setToolContext(tc);
        }

        ModelRequest enhancedRequest = ModelRequest.builder(request).build();
        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "DynamicToolPromptInterceptor ";
    }
}
