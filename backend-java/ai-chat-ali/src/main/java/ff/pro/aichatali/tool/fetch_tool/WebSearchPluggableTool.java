package ff.pro.aichatali.tool.fetch_tool;

import ff.pro.aichatali.tool.PluggableTool;
import lombok.Getter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@ConditionalOnProperty(name = "tools.websearch.enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchPluggableTool implements PluggableTool {

    @Autowired
    private WebSearchTool webSearchTool;

    private final String name = "webSearchTool";
    private final String title = "联网搜索";
    private final String description = "联网搜索最新信息, 优先级低于知识库搜索，当知识库无相关内容或需要实时数据时调用";
    private final String toolIcon = "fa-globe";

    @Override
    public ToolCallback getToolCallback() {
        return FunctionToolCallback.builder(this.getName(), webSearchTool)
                .description(getDescription())
                .inputType(WebSearchTool.Input.class)
                .build();
    }
}
