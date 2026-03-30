package ff.pro.aichatali.tool.rag_tool;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.config.LoggingModelInterceptor;
import ff.pro.aichatali.config.ToolCallCapture;
import ff.pro.aichatali.service.MilvusHybridRetrieverService;
import ff.pro.aichatali.tool.fetch_tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
public class RagAgentTool implements BiFunction<MilvusHybridRetrieverService.Input, ToolContext, String> {

    private static final String SYSTEM_PROMPT = """
            你是一个智能助手，可以访问多个信息源来回答问题。
            使用工具时：
            1. 优先使用 ragSearch 搜索文档库
            2. 如果需要最新信息，使用 webSearch
            3. 基于检索到的信息生成准确、完整的答案
            4. 如果信息不足，可以多次调用工具
            """;

    private final ReactAgent reactAgent;

    public RagAgentTool(ChatModel chatModel,
                        RagTool ragTool,
                        WebSearchTool webSearchTool,
                        int maxIterations,
                        ToolCallCapture toolCallCapture,
                        LoggingModelInterceptor loggingModelInterceptor) {
        ToolCallback docSearch = FunctionToolCallback
                .builder("ragSearch", ragTool)
                .description("搜索内部知识库文档")
                .inputType(MilvusHybridRetrieverService.Input.class)
                .build();
        ToolCallback webSearch = FunctionToolCallback
                .builder("webSearch", webSearchTool)
                .description("联网搜索最新信息")
                .inputType(WebSearchTool.Input.class)
                .build();
        this.reactAgent = ReactAgent.builder()
                .name("ragAgent")
                .model(chatModel)
                .tools(List.of(docSearch, webSearch))
                .systemPrompt(SYSTEM_PROMPT)
                .interceptors(toolCallCapture, loggingModelInterceptor)
                .compileConfig(CompileConfig.builder().recursionLimit(maxIterations).build())
                .saver(new MemorySaver())
                .build();
    }

    @Override
    public String apply(MilvusHybridRetrieverService.Input input, ToolContext toolContext) {
        String query = input != null && input.query() != null ? input.query() : "";
        String threadId = toolContext != null
                ? (String) toolContext.getContext().getOrDefault(SysContext.THREAD_ID, null)
                : null;
        RunnableConfig config = RunnableConfig.builder()
                .addMetadata(SysContext.THREAD_ID, threadId)
                .addMetadata(SysContext.PARENT_NODE, "ragAgentTool")
                .build();
        try {
            AssistantMessage response = reactAgent.call(Map.of("input", query), config);
            return response.getText();
        } catch (Exception e) {
            log.error("RagAgentTool failed: {}", e.getMessage(), e);
            return "知识库检索失败: " + e.getMessage();
        }
    }
}
