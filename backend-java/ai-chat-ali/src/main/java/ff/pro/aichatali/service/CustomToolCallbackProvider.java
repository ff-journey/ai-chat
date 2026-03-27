package ff.pro.aichatali.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

public class CustomToolCallbackProvider implements ToolCallbackProvider {
    @Override
    public ToolCallback[] getToolCallbacks() {
        return new ToolCallback[0];
    }
}
