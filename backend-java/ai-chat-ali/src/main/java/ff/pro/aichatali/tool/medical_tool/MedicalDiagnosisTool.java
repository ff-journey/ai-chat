package ff.pro.aichatali.tool.medical_tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.common.ToolResult;
import ff.pro.aichatali.config.MedicalToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 医疗诊断工具：将结构化临床信息交给 vLLM（qwen3-0.6b，医疗 CoT LoRA）做逐步推理，
 * 返回推理链供 Supervisor LLM 整合后呈现给用户。
 *
 * 影像分类（CNN）已解耦，由 pneumoniaCnnTool 独立处理。
 * 若已有影像检查结果，请将其填入 clinicalMaterials 字段一并传入。
 */
public class MedicalDiagnosisTool implements BiFunction<MedicalDiagnosisTool.Input, ToolContext, ToolResult> {

    public record Input(
            String patientInfo,
            String clinicalMaterials
    ) {}

    private static final Logger log = LoggerFactory.getLogger(MedicalDiagnosisTool.class);

    private static final String SYSTEM_PROMPT = """
            你是一个经过医学训练的诊断推理专家。请对以下临床信息进行系统性推理：
            1. 分析主要症状和检查结果
            2. 列出可能的鉴别诊断，并说明每项依据
            3. 给出初步诊断结论
            4. 提出处理建议
            注意：结论仅供参考，不能替代专业医生诊断。
            """;

    private final MedicalToolConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicalDiagnosisTool(MedicalToolConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public ToolResult apply(Input input, ToolContext toolContext) {
        if (input == null || (isBlank(input.patientInfo()) && isBlank(input.clinicalMaterials()))) {
            return ToolResult.error("No patient information provided.");
        }

        StringBuilder structuredQuery = new StringBuilder();
        if (!isBlank(input.clinicalMaterials())) {
            structuredQuery.append("【检查材料】\n").append(input.clinicalMaterials()).append("\n\n");
        }
        if (!isBlank(input.patientInfo())) {
            structuredQuery.append("【患者主诉】\n").append(input.patientInfo());
        }

        if (!config.getVllm().isEnabled()) {
            return ToolResult.ok(mockResult());
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", config.getVllm().getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", structuredQuery.toString())
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2048
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getVllm().getUrl(), HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String raw = root.path("choices").get(0).path("message").path("content").asText();
                String result = raw.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
                return ToolResult.ok(result);
            }
            return ToolResult.error("vLLM service returned unexpected response: " + response.getStatusCode());
        } catch (Exception e) {
            log.warn("vLLM service unavailable, returning mock result: {}", e.getMessage());
            return ToolResult.ok(mockResult());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String mockResult() {
        return "Based on the provided patient information, here is a preliminary assessment:\n\n" +
                "1. Symptoms noted from input\n" +
                "2. Recommended further examinations: blood test, chest X-ray\n" +
                "3. Please consult a professional physician for accurate diagnosis\n\n" +
                "[MOCK - vLLM service not connected]";
    }
}
