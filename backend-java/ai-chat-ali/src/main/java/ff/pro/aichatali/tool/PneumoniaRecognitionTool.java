package ff.pro.aichatali.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.config.MedicalToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * CNN-based pneumonia recognition tool.
 * Calls a Python CNN service to classify chest X-ray images.
 * Falls back to a mock result when the service is unavailable.
 */
public class PneumoniaRecognitionTool implements BiFunction<String, ToolContext, String> {

    private static final Logger log = LoggerFactory.getLogger(PneumoniaRecognitionTool.class);

    private final MedicalToolConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PneumoniaRecognitionTool(MedicalToolConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public String apply(String input, ToolContext toolContext) {
        // Try to get uploaded image from ToolContext metadata
        String imageBase64 = null;
        if (toolContext != null && toolContext.getContext() != null) {
            Object img = toolContext.getContext().get("uploaded_image_base64");
            if (img != null) {
                imageBase64 = img.toString();
            }
        }

        if (imageBase64 == null || imageBase64.isBlank()) {
            return "{\"error\": \"No chest X-ray image provided. Please upload an image for pneumonia detection.\"}";
        }

        if (!config.getCnn().isEnabled()) {
            return mockResult();
        }

        try {
            Map<String, String> requestBody = Map.of(
                    "image_base64", imageBase64,
                    "image_format", "jpeg"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getCnn().getUrl(), HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.path("data");
                String prediction = data.path("prediction").asText("UNKNOWN");
                double confidence = data.path("confidence").asDouble(0.0);
                return String.format(
                        "Pneumonia detection result: %s (confidence: %.2f%%)",
                        prediction, confidence * 100);
            }
            return "CNN service returned unexpected response: " + response.getStatusCode();
        } catch (Exception e) {
            log.warn("CNN service unavailable, returning mock result: {}", e.getMessage());
            return mockResult();
        }
    }

    private String mockResult() {
        return "Pneumonia detection result: PNEUMONIA (confidence: 85.00%) [MOCK - CNN service not connected]";
    }

}
