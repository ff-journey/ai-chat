package ff.pro.aichatali.tool.rag_tool;

import ff.pro.aichatali.service.MilvusHybridRetrieverService;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tools.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagPluggableTool implements PluggableTool {

    @Autowired
    private MilvusHybridRetrieverService milvusHybridRetrieverService;

    @Override
    public String name() { return "ragTool"; }

    @Override
    public String description() {
        return "内部知识库检索。用于查询本系统沉淀的私域文档、业务规则、产品说明。不适用于需要实时互联网数据的问题。";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("ragTool", milvusHybridRetrieverService)
                .description(description())
                .inputType(MilvusHybridRetrieverService.Input.class)
                .build();
    }
}
