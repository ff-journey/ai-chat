package ff.pro.aichatali.tool.rag_agent_tool;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import ff.pro.aichatali.service.MemoryHybridRetrieverService;
import ff.pro.aichatali.service.websearch.WebSearchTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class RagAgentTool implements BiFunction<RagAgentTool.Input, ToolContext, String> {

    public record Input(String query) {}

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，可以访问多个信息源来回答问题。
            使用工具时：
            1. 优先使用 document_search 搜索文档库
            2. 如果需要最新信息，使用 web_search
            3. 基于检索到的信息生成准确、完整的答案
            4. 如果信息不足，可以多次调用工具
            """;

    private final ReactAgent reactAgent;

    public RagAgentTool(ChatModel chatModel,
                        MemoryHybridRetrieverService retrieverService,
                        WebSearchTool webSearchTool,
                        int maxIterations) {
        ToolCallback docSearch = FunctionToolCallback
                .builder("document_search", retrieverService)
                .description("搜索内部知识库文档")
                .inputType(MemoryHybridRetrieverService.Input.class)
                .build();
        ToolCallback webSearch = FunctionToolCallback
                .builder("web_search", webSearchTool)
                .description("联网搜索最新信息")
                .inputType(WebSearchTool.Input.class)
                .build();
        this.reactAgent = ReactAgent.builder()
                .name("rag_agent")
                .model(chatModel)
                .tools(List.of(docSearch, webSearch))
                .systemPrompt(SYSTEM_PROMPT)
                .compileConfig(CompileConfig.builder().recursionLimit(maxIterations).build())
                .saver(new MemorySaver())
                .build();
    }

    @Override
    public String apply(Input input, ToolContext toolContext) {
        String query = input != null && input.query() != null ? input.query() : "";
        try {
            AssistantMessage response = reactAgent.call(
                    Map.of("input", query),
                    RunnableConfig.builder().build());
            return response.getText();
        } catch (GraphRunnerException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
