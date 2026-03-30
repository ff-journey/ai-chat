package ff.pro.aichatali.tool.rag_tool;

import ff.pro.aichatali.service.MilvusHybridRetrieverService;
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
@ConditionalOnProperty(name = "tools.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagPluggableTool implements PluggableTool {

    @Autowired
    private RagTool ragTool;

    private final String name = "ragTool";
    private final String title = "知识库";
    private final String description = "内部知识库检索, 优先级大于联网搜索。用于查询本系统沉淀的私域文档、业务规则、产品说明。不适用于需要实时互联网数据的问题。";
    private final String toolIcon = "fa-book";

    @Override
    public List<String> getMutuallyExclusiveWith() { return List.of("ragAgentTool"); }

    @Override
    public ToolCallback getToolCallback() {
        return FunctionToolCallback.builder(this.getName(), ragTool)
                .description(getDescription())
                .inputType(MilvusHybridRetrieverService.Input.class)
                .build();
    }
}
