package ff.pro.aichatali.tool.medical_tool;

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
 * 纯文字医疗诊断工具。与 pneumoniaCnnTool 的区别：本工具提供诊断推理，后者仅做影像分类。
 */
@Component
@Getter
@ConditionalOnProperty(name = "tools.medical-diagnosis.enabled", havingValue = "true", matchIfMissing = true)
public class MedicalDiagnosisPluggableTool implements PluggableTool {

    @Autowired
    private MedicalToolConfig medicalToolConfig;

    @Autowired
    private RestTemplate medicalRestTemplate;

    private final String name = "medicalDiagnosisTool";
    private final String title = "医疗问诊";
//    private final String description = """
//            专业医疗诊断推理, 不提供医疗知识问询：根据患者主诉和已有检查材料进行系统性推理，给出鉴别诊断和处理建议（不替代专业医生）
//             → 若症状信息充足，汇总病情信息后直接调用 medicalDiagnosisTool
//             → 若症状信息不足（缺少持续时间、既往病史、用药史），主动追问后再调用medicalDiagnosisTool
//            """;
    private final String description = """
            医疗诊断推理：基于患者症状和检查结果进行鉴别诊断推理。调用前应确保已通过知识检索获取相关参考资料（如有）。症状信息不足时先向用户追问。
            """;
    private final String toolIcon = "fa-stethoscope";

    @Override
    public ToolCallback getToolCallback() {
        return FunctionToolCallback.builder(this.getName(),
                new MedicalDiagnosisTool(medicalToolConfig, medicalRestTemplate))
                .description(getDescription())
                .inputType(MedicalDiagnosisTool.Input.class)
                .build();
    }
}
