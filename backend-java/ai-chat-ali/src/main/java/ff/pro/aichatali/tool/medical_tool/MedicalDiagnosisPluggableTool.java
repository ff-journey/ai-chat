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
    public String name() { return "medicalDiagnosis"; }

    @Override
    public String description() {
        return "专业医疗诊断推理：根据患者主诉和已有检查材料进行系统性推理，给出鉴别诊断和处理建议（不替代专业医生）。" +
                "patientInfo: 患者主诉和症状描述；" +
                "clinicalMaterials: 已完成的检查结果，如影像分类结论（可先调用 pneumoniaCnnTool 获取），可为空。";
    }

    @Override
    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("medicalDiagnosis",
                new MedicalDiagnosisTool(medicalToolConfig, medicalRestTemplate))
                .description(description())
                .inputType(MedicalDiagnosisTool.Input.class)
                .build();
    }
}
