package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.service.ToolRegistryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

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

        ModelRequest enhancedRequest = ModelRequest.builder(request).build();
        return handler.call(enhancedRequest);
    }

    @Override
    public String getName() {
        return "DynamicToolPromptInterceptor ";
    }
}
