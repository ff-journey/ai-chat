package ff.pro.aichatali.tool.rag_agent_tool;

import ff.pro.aichatali.service.MemoryHybridRetrieverService;
import ff.pro.aichatali.service.websearch.WebSearchTool;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tools.rag-agent.enabled", havingValue = "true", matchIfMissing = false)
public class RagAgentPluggableTool implements PluggableTool {

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel chatModel;

    @Autowired
    private MemoryHybridRetrieverService retrieverService;

    @Autowired
    private WebSearchTool webSearchTool;

    @Value("${tools.rag-agent.max-iterations:5}")
    private int maxIterations;

    @Override
    public String name() { return "rag_agent"; }

    @Override
    public String description() {
        return "混合检索助手：优先搜索内部知识库，必要时联网补充最新信息，综合生成完整答案";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("rag_agent",
                        new RagAgentTool(chatModel, retrieverService, webSearchTool, maxIterations))
                .description(description())
                .inputType(RagAgentTool.Input.class)
                .build();
    }
}
