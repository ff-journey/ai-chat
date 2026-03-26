package ff.pro.aichatali.tool.feiyan_tool;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import ff.pro.aichatali.config.MedicalToolConfig;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * CNN 肺炎影像分析 + 医疗专家 Agent 联合工具。
 * medicalAgent 通过 @Lazy 注入，避免与 supervisorAgent 产生循环依赖。
 */
@Component
@ConditionalOnProperty(name = "tools.pneumonia.enabled", havingValue = "true", matchIfMissing = true)
public class FeiyanPluggableTool implements PluggableTool {

    @Autowired
    private MedicalToolConfig medicalToolConfig;

    @Autowired
    private RestTemplate medicalRestTemplate;

    @Lazy
    @Autowired
    @Qualifier("medicalAgent")
    private ReactAgent medicalAgent;

    @Override
    public String name() { return "feiyanAgentMedicalTool"; }

    @Override
    public String description() { return "识别胸部X光影像，给出专业诊断报告"; }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("feiyanAgentMedicalTool",
                new FeiyanAgentMedicalTool(medicalToolConfig, medicalRestTemplate, medicalAgent))
                .description(description())
                .inputType(FeiyanAgentMedicalTool.Input.class)
                .build();
    }
}
