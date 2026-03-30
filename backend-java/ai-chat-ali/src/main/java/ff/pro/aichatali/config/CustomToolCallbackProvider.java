package ff.pro.aichatali.config;

import ff.pro.aichatali.common.RequestContext;
import ff.pro.aichatali.common.UserSimpleDto;
import ff.pro.aichatali.service.ToolRegistryService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CustomToolCallbackProvider implements ToolCallbackProvider {

    final ObjectProvider<ToolRegistryService> toolRegistryService;

    @NotNull
    @Override
    public ToolCallback[] getToolCallbacks() {
        return  Objects.requireNonNull(toolRegistryService.getIfAvailable()).getTools().toArray(new ToolCallback[0]);
//        RequestContext requestContext = RequestContext.getRequestContext();
//        UserSimpleDto userInfo = requestContext.getUserInfo();
//
//        int toolFlag = requestContext.getToolFlag();
//        return Objects.requireNonNull(toolRegistryService.getIfAvailable()).dynamicCallbacks(toolFlag).toArray(ToolCallback[]::new);
    }

}
