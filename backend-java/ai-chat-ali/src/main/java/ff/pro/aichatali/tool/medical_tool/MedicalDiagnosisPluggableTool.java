package ff.pro.aichatali.tool.medical_tool;

import ff.pro.aichatali.config.MedicalToolConfig;
import ff.pro.aichatali.tool.PluggableTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 完整医疗诊断工具：有影像时自动调用 CNN 分类，再交给 vLLM CoT 模型推理。
 * 纯文字问诊同样支持。与 pneumoniaCnnTool 的区别：本工具提供诊断推理，后者仅做影像分类。
 */
@Component
@ConditionalOnProperty(name = "tools.medical-diagnosis.enabled", havingValue = "true", matchIfMissing = false)
public class MedicalDiagnosisPluggableTool implements PluggableTool {

    @Autowired
    private MedicalToolConfig medicalToolConfig;

    @Autowired
    private RestTemplate medicalRestTemplate;

    @Override
    public String name() { return "medical_diagnosis"; }

    @Override
    public String description() {
        return "专业医疗诊断：根据患者症状和影像检查结果，进行系统性推理并给出诊断建议（不替代专业医生）";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("medical_diagnosis",
                new MedicalDiagnosisTool(medicalToolConfig, medicalRestTemplate))
                .description(description())
                .inputType(String.class)
                .build();
    }
}
