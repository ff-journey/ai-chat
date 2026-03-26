package ff.pro.aichatali.tool.medical_tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.config.MedicalToolConfig;
import ff.pro.aichatali.tool.feiyan_tool.FeiyanCnnTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 完整医疗诊断工具：若有影像则先调 CNN 分类，再将结构化临床信息交给
 * vLLM（qwen3-0.6b，医疗 CoT LoRA）做逐步推理，最终返回推理链供
 * Supervisor LLM 整合后呈现给用户。
 */
public class MedicalDiagnosisTool implements BiFunction<String, ToolContext, String> {

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
    private final FeiyanCnnTool cnnTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicalDiagnosisTool(MedicalToolConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.cnnTool = new FeiyanCnnTool(config, restTemplate);
    }

    @Override
    public String apply(String patientInfo, ToolContext toolContext) {
        if (patientInfo == null || patientInfo.isBlank()) {
            return "{\"error\": \"No patient information provided.\"}";
        }

        // 如果上下文中有影像，先做 CNN 分类，把结果拼入结构化 query
        StringBuilder structuredQuery = new StringBuilder();
        Object img = toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext().get("uploaded_image_path") : null;
        if (img != null) {
            String cnnResult = cnnTool.apply(patientInfo, toolContext);
            structuredQuery.append("【影像检查结果】\n").append(cnnResult).append("\n\n");
        }
        structuredQuery.append("【患者主诉】\n").append(patientInfo);

        if (!config.getVllm().isEnabled()) {
            return mockResult(patientInfo);
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
                // 去掉 CoT <think> 标签（部分模型输出格式）
                return raw.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
            }
            return "vLLM service returned unexpected response: " + response.getStatusCode();
        } catch (Exception e) {
            log.warn("vLLM service unavailable, returning mock result: {}", e.getMessage());
            return mockResult(patientInfo);
        }
    }

    private String mockResult(String patientInfo) {
        return "Based on the provided patient information, here is a preliminary assessment:\n\n" +
                "1. Symptoms noted from input\n" +
                "2. Recommended further examinations: blood test, chest X-ray\n" +
                "3. Please consult a professional physician for accurate diagnosis\n\n" +
                "[MOCK - vLLM service not connected]";
    }
}
