package ff.pro.aichatali.tool.fetch_tool;

import ff.pro.aichatali.service.websearch.WebSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import ff.pro.aichatali.common.ToolResult;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 联网搜索独立 Tool，可直接注册为任意 Agent 的 FunctionToolCallback。
 *
 * 用法示例：
 * <pre>
 *   FunctionToolCallback.builder("webSearch", webSearchTool)
 *       .description("联网搜索最新信息，当知识库无相关内容时调用")
 *       .inputType(WebSearchTool.Input.class)
 *       .build();
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSearchTool implements BiFunction<WebSearchTool.Input, ToolContext, ToolResult> {

    public record Input(String query) {}

    private static final int DEFAULT_TOP_K = 5;

    final WebSearchPort webSearchPort;

    @Override
    public ToolResult apply(Input input, ToolContext toolContext) {
        try {
            List<Document> results = webSearchPort.search(input.query(), DEFAULT_TOP_K);
            if (results.isEmpty()) {
                return ToolResult.ok("联网搜索未找到相关内容");
            }
            String content = results.stream()
                    .map(d -> {
                        String url = d.getMetadata().getOrDefault("url", "").toString();
                        String text = d.getText();
                        return StringUtils.isNotBlank(url) ? text + "\n来源: " + url : text;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));
            return ToolResult.ok(content);
        } catch (Exception e) {
            log.error("WebSearchTool error: {}", e.getMessage(), e);
            return ToolResult.error("联网搜索失败: " + e.getMessage());
        }
    }
}
