package ff.pro.aichatali.tool.feiyan_tool;

import ff.pro.aichatali.config.MedicalToolConfig;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 胸部 X 光 CNN 快速分类工具（仅分类，不做诊断解释）。
 * 需要完整诊断报告时请使用 medical_diagnosis 工具。
 */
@Component
@ConditionalOnProperty(name = "tools.pneumonia.enabled", havingValue = "true", matchIfMissing = true)
public class FeiyanPluggableTool implements PluggableTool {

    @Autowired
    private MedicalToolConfig medicalToolConfig;

    @Autowired
    private RestTemplate medicalRestTemplate;

    @Override
    public String name() { return "pneumoniaCnnTool"; }

    @Override
    public String description() {
        return "胸部X光影像肺炎快速分类，返回是否肺炎及置信度。仅做影像分类，不提供诊断建议。";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("pneumoniaCnnTool",
                new FeiyanCnnTool(medicalToolConfig, medicalRestTemplate))
                .description(description())
                .inputType(String.class)
                .build();
    }
}
