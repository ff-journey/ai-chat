package ff.pro.aichatali.tool.medical_tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Medical diagnosis tool powered by vLLM (qwen3-0.6b).
 * Calls an OpenAI-compatible endpoint for medical consultation.
 * Falls back to a mock result when the service is unavailable.
 */
public class MedicalDiagnosisTool implements BiFunction<String, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(MedicalDiagnosisTool.class);

    private final MedicalToolConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicalDiagnosisTool(MedicalToolConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public String apply(String patientInfo, ToolContext toolContext) {
        if (patientInfo == null || patientInfo.isBlank()) {
            return "{\"error\": \"No patient information provided.\"}";
        }

        if (!config.getVllm().isEnabled()) {
            return mockResult(patientInfo);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", config.getVllm().getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "你是一个专业的医疗诊断助手。根据患者提供的信息，给出初步诊断建议。" +
                                    "请注意：这仅供参考，不能替代专业医生的诊断。"),
                            Map.of("role", "user", "content", patientInfo)
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
                return root.path("choices").get(0).path("message").path("content").asText();
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
