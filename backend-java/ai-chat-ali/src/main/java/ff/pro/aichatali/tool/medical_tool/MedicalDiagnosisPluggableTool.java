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
 * 纯 vLLM 医疗问诊工具（不依赖 CNN，不依赖其他 Agent）。
 * 与 FeiyanPluggableTool 分离，可独立启用/禁用。
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
        return "专业医疗问诊，根据患者描述的症状给出初步诊断建议（不替代专业医生）";
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
