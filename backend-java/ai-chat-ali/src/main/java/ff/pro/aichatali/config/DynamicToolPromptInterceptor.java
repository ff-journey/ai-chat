package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.service.ToolRegistryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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
        List<ToolCallback> toolCallbacks = toolRegistryService.dynamicCallbacks((int) toolFlag);
        request.getOptions().setToolCallbacks(toolCallbacks);
//        SystemMessage sysMessage = request.getSystemMessage();
//        String toolDescription = toolCallbacks.stream()
//                .map(it -> " - %s : %s".formatted(it.getToolDefinition().name(), it.getToolDefinition().description()))
//                .collect(Collectors.joining("\n"));
//
//        SystemMessage newSysMessage = SystemMessage.builder().text(sysMessage.getText()+(toolDescription)).build();

        ModelRequest enhancedRequest = ModelRequest.builder(request).build();
        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "DynamicToolPromptInterceptor ";
    }
}
