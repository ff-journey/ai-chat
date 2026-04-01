package ff.pro.aichatali.tool.feiyan_tool;

import ff.pro.aichatali.config.MedicalToolConfig;
import ff.pro.aichatali.tool.PluggableTool;
import lombok.Getter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 胸部 X 光 CNN 快速分类工具（仅分类，不做诊断解释）。
 * 需要完整诊断报告时请使用 medicalDiagnosisTool 工具。
 */
@Component
@Getter
@ConditionalOnProperty(name = "tools.pneumonia.enabled", havingValue = "true", matchIfMissing = true)
public class FeiyanPluggableTool implements PluggableTool {

    @Autowired
    private MedicalToolConfig medicalToolConfig;

    @Autowired
    private RestTemplate medicalRestTemplate;

    private final String name = "pneumoniaCnnTool";

    private final String title = "肺炎分析";

    private final String description = "胸部X光影像肺炎快速分类：接收用户上传的胸部X光图像，通过CNN模型分析，返回是否肺炎及置信度。仅做影像分类，不提供诊断建议。";

    private final String toolIcon = "fa-lungs";


    @Override
    public ToolCallback getToolCallback() {
        return FunctionToolCallback.builder(this.getName(),
                new FeiyanCnnTool(medicalToolConfig, medicalRestTemplate))
                .description(getDescription())
                .inputType(FeiyanCnnTool.Input.class)
                .build();
    }
}
