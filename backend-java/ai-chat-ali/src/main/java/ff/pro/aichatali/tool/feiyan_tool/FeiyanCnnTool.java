package ff.pro.aichatali.tool.feiyan_tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.common.ToolResult;
import ff.pro.aichatali.config.MedicalToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * CNN-based pneumonia recognition tool.
 * Calls a Python CNN service to classify chest X-ray images.
 * Falls back to a mock result when the service is unavailable.
 */
public class FeiyanCnnTool implements BiFunction<FeiyanCnnTool.Input, ToolContext, ToolResult> {

    public record Input(String description) {}

    private static final Logger log = LoggerFactory.getLogger(FeiyanCnnTool.class);

    private final MedicalToolConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeiyanCnnTool(MedicalToolConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public ToolResult apply(Input input, ToolContext toolContext) {
        String imagePath = null;
        if (toolContext != null && toolContext.getContext() != null) {
            Object img = toolContext.getContext().get("uploaded_image_path");
            if (img != null) {
                imagePath = img.toString();
            }
        }

        if (imagePath == null || imagePath.isBlank()) {
            return ToolResult.error("No chest X-ray image provided. Please upload an image for pneumonia detection.");
        }

        if (!config.getCnn().isEnabled()) {
            return ToolResult.ok(mockResult());
        }

        try {
            boolean fileMode = "file".equalsIgnoreCase(config.getCnn().getTransferMode());
            ResponseEntity<String> response;

            if (fileMode) {
                // file mode: read image bytes, POST as multipart/form-data to /upload endpoint
                byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
                String filename = Path.of(imagePath).getFileName().toString();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new ByteArrayResource(imageBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                response = restTemplate.exchange(
                        config.getCnn().getUrl() + "/upload", HttpMethod.POST, entity, String.class);
            } else {
                // path mode: POST JSON file_path (original logic)
                Map<String, String> requestBody = Map.of("file_path", imagePath);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
                response = restTemplate.exchange(
                        config.getCnn().getUrl(), HttpMethod.POST, entity, String.class);
            }

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return ToolResult.ok(root.toString());
            }
            return ToolResult.error("CNN service returned unexpected response: " + response.getStatusCode());
        } catch (Exception e) {
            log.warn("CNN service unavailable, returning mock result: {}", e.getMessage());
            return ToolResult.ok(mockResult());
        }
    }

    private String mockResult() {
        return "Pneumonia detection result: PNEUMONIA (confidence: 85.00%) [MOCK - CNN service not connected]";
    }
}
