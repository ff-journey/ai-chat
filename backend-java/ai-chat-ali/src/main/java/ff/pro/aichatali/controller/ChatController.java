package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Primary chat endpoints. All variants emit JSON SSE events:
 *   {"type":"token","text":"..."}   — streaming token
 *   {"type":"tool_done"}            — a tool call completed
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    @Autowired
    @Qualifier("supervisor_agent")
    private ReactAgent supervisorAgent;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    record ChatRequest(String message, String userId, String sessionId) {}

    /** Text-only chat via JSON body. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest req) {
        String threadId = resolveThreadId(req.userId(), req.sessionId());
        chatHistoryService.addMessage(threadId, "user", req.message());
        return doStream(threadId, req.message(), null, req.message());
    }

    /** Image + text chat via multipart form. */
    @PostMapping(value = "/stream/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "message", defaultValue = "") String message,
            @RequestParam("userId") String userId,
            @RequestParam("sessionId") String sessionId) {

        String threadId = resolveThreadId(userId, sessionId);
        String savedPath = saveUploadedFile(image);
        String imageUrl = "/uploads/" + Paths.get(savedPath).getFileName();
        String imgSignal = "[用户已上传胸部X光图片，请进行肺炎影像分析]";
        String fullMessage = message.isBlank() ? imgSignal : message + "\n" + imgSignal;
        chatHistoryService.addMessage(threadId, "user", fullMessage, imageUrl);
        return doStream(threadId, fullMessage, savedPath,
                message.isBlank() ? "胸部X光分析" : message);
    }

    /** Sample image chat via form params. */
    @PostMapping(value = "/stream/sample", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSample(
            @RequestParam("sampleId") String sampleId,
            @RequestParam(value = "message", defaultValue = "") String message,
            @RequestParam("userId") String userId,
            @RequestParam("sessionId") String sessionId) {

        String threadId = resolveThreadId(userId, sessionId);
        // sampleId format: "COVID/image1.jpg"
        Path samplePath = Paths.get(samplesDir, sampleId);
        if (!Files.exists(samplePath) || !Files.isRegularFile(samplePath)) {
            return Flux.error(new RuntimeException("Sample not found: " + sampleId));
        }
        String imgSignal = "[用户已上传胸部X光图片，请进行肺炎影像分析]";
        String fullMessage = message.isBlank()
                ? "帮我分析这张胸部X光\n" + imgSignal
                : message + "\n" + imgSignal;
        String sampleImageUrl = "/api/samples/" + sampleId;
        chatHistoryService.addMessage(threadId, "user", fullMessage, sampleImageUrl);
        return doStream(threadId, fullMessage, samplePath.toAbsolutePath().toString(),
                message.isBlank() ? "胸部X光样本分析" : message);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Flux<String> doStream(String threadId, String message, String imagePath, String titleHint) {
        RunnableConfig.Builder cfgBuilder = RunnableConfig.builder().threadId(threadId);
        if (imagePath != null) cfgBuilder.addMetadata("uploaded_image_path", imagePath);
        RunnableConfig config = cfgBuilder.build();

        StringBuilder aiResponse = new StringBuilder();
        try {
            return supervisorAgent.stream(message, config)
                    .filter(o -> o instanceof StreamingOutput)
                    .map(o -> {
                        StreamingOutput so = (StreamingOutput) o;
                        try {
                            if (so.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                                String text = so.message().getText();
                                if (text != null && !text.isEmpty()) {
                                    aiResponse.append(text);
                                    return jsonEvent("token", Map.<String, Object>of("text", text));
                                }
                            } else if (so.getOutputType() == OutputType.AGENT_TOOL_FINISHED) {
                                return jsonEvent("tool_done", Map.<String, Object>of());
                            }
                        } catch (Exception e) {
                            log.debug("SSE map error: {}", e.getMessage());
                        }
                        return "";
                    })
                    .filter(s -> !s.isEmpty())
                    .doOnComplete(() -> {
                        if (!aiResponse.isEmpty()) {
                            chatHistoryService.addMessage(threadId, "assistant", aiResponse.toString());
                            // Auto-set title from first user message if not yet set
                            if (chatHistoryService.getTitle(threadId) == null) {
                                chatHistoryService.setTitle(threadId, truncate(titleHint, 20));
                            }
                        }
                    });
        } catch (GraphRunnerException e) {
            log.error("Stream error for thread {}: {}", threadId, e.getMessage());
            return Flux.error(e);
        }
    }

    private String jsonEvent(String type, Map<String, Object> extra) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.putAll(extra);
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"" + type + "\"}";
        }
    }

    private String resolveThreadId(String userId, String sessionId) {
        if (userId == null || userId.isBlank() || "default".equals(userId)) return sessionId;
        return userId + "_" + sessionId;
    }

    private String truncate(String s, int max) {
        if (s == null || s.isBlank()) return "新对话";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String saveUploadedFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(System.getProperty("user.dir"), uploadDir);
            Files.createDirectories(uploadPath);
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path dest = uploadPath.resolve(filename);
            file.transferTo(dest.toFile());
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image", e);
        }
    }
}
