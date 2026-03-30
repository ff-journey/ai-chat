package ff.pro.aichatali.tool.rag_tool;

import ff.pro.aichatali.config.LoggingModelInterceptor;
import ff.pro.aichatali.config.ToolCallCapture;
import ff.pro.aichatali.service.MilvusHybridRetrieverService;
import ff.pro.aichatali.tool.fetch_tool.WebSearchTool;
import ff.pro.aichatali.tool.PluggableTool;
import lombok.Getter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@ConditionalOnProperty(name = "tools.rag-agent.enabled", havingValue = "true", matchIfMissing = true)
public class RagAgentPluggableTool implements PluggableTool {

    @Lazy
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private RagTool ragTool;
    @Autowired
    private WebSearchTool webSearchTool;
    @Autowired
    private ToolCallCapture toolCallCapture;
    @Autowired
    private LoggingModelInterceptor loggingModelInterceptor;

    @Value("${tools.rag-agent.max-iterations:5}")
    private int maxIterations;

    private final String name = "ragAgentTool";
    private final String title = "混合检索";
    private final String description = "混合检索助手：优先搜索内部知识库，必要时联网补充最新信息，综合生成完整答案";
    private final String toolIcon = "fa-robot";

    @Override
    public List<String> getMutuallyExclusiveWith() { return List.of("ragTool", "webSearchTool"); }

    @Override
    public ToolCallback getToolCallback() {
        return FunctionToolCallback.builder(this.getName(),
                        new RagAgentTool(chatModel, ragTool, webSearchTool, maxIterations,
                                toolCallCapture, loggingModelInterceptor))
                .description(getDescription())
                .inputType(MilvusHybridRetrieverService.Input.class)
                .build();
    }
}
