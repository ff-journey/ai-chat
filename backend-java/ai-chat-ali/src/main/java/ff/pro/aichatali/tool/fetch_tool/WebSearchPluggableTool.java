package ff.pro.aichatali.tool.fetch_tool;

import ff.pro.aichatali.service.websearch.WebSearchTool;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tools.websearch.enabled", havingValue = "true", matchIfMissing = false)
public class WebSearchPluggableTool implements PluggableTool {

    @Autowired
    private WebSearchTool webSearchTool;

    @Override
    public String name() { return "web_search"; }

    @Override
    public String description() {
        return "联网搜索最新信息，当知识库无相关内容或需要实时数据时调用";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("web_search", webSearchTool)
                .description(description())
                .inputType(WebSearchTool.Input.class)
                .build();
    }
}
