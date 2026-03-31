package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import ff.pro.aichatali.common.RequestContext;
import ff.pro.aichatali.common.SysContext;
import ff.pro.aichatali.common.SpanContext;
import ff.pro.aichatali.config.SseService;
import ff.pro.aichatali.config.TraceEvent;
import ff.pro.aichatali.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    @Qualifier("supervisorAgent")
    private ReactAgent supervisorAgent;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SseService sseService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    @Value("${app.stream.timeout-minutes:5}")
    private int streamTimeoutMinutes;

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

    private Flux<String> doStream(String threadId, String message, String imagePath,
                                  String titleHint) {
        RunnableConfig.Builder cfgBuilder = RunnableConfig.builder().threadId(threadId);
        if (imagePath != null) cfgBuilder.addMetadata("uploaded_image_path", imagePath);
        cfgBuilder.addMetadata(SysContext.TOOL_FLAG, RequestContext.getRequestContext().getToolFlag());
        cfgBuilder.addMetadata(SysContext.THREAD_ID, threadId);

        SpanContext rootSpan = SpanContext.create(SpanContext.SpanType.SUPERVISOR, "supervisor", null);
        cfgBuilder.addMetadata(SysContext.CURRENT_SPAN_ID, rootSpan.spanId());
        RunnableConfig config = cfgBuilder.build();

        sseService.createSink(threadId);
        sseService.push(threadId, TraceEvent.spanStart(rootSpan));

        // Per-round buffer — reset on each new STREAMING after a FINISHED
        AtomicReference<StringBuilder> roundBuffer = new AtomicReference<>(new StringBuilder());
        AtomicReference<String> lastFinalAnswer = new AtomicReference<>("");
        AtomicBoolean roundFinished = new AtomicBoolean(false);
        AtomicInteger roundCounter = new AtomicInteger(1);
        AtomicBoolean hadError = new AtomicBoolean(false);

        try {
            Sinks.Empty<Void> doneSink = Sinks.empty();

            Flux<String> tokens = supervisorAgent.stream(message, config)
                    .filter(o -> o instanceof StreamingOutput)
                    .flatMap(o -> {
                        StreamingOutput<?> so = (StreamingOutput<?>) o;
                        try {
                            OutputType type = so.getOutputType();
                            String text = so.message() != null ? so.message().getText() : null;

                            if (type == OutputType.AGENT_MODEL_STREAMING) {
                                // Detect new round: previous round was FINISHED, now STREAMING again
                                if (roundFinished.getAndSet(false)) {
                                    int round = roundCounter.incrementAndGet();
                                    roundBuffer.set(new StringBuilder());
                                    log.info("[SPAN] [{}] round:{} started", threadId, round);
                                }
                                if (text != null && !text.isEmpty()) {
                                    roundBuffer.get().append(text);
                                    return Flux.just(jsonEvent("token", Map.<String, Object>of("text", text)));
                                }
                            } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                String streamed = roundBuffer.get().toString();
                                int round = roundCounter.get();
                                roundFinished.set(true);

                                log.info("[SPAN] [{}] round:{} finished text_len={} streamed_len={}",
                                        threadId, round,
                                        text != null ? text.length() : 0, streamed.length());

                                // Save this round's final content (overwrite, don't accumulate)
                                if (!streamed.isEmpty()) {
                                    lastFinalAnswer.set(streamed);
                                }

                                // Fallback: compensate for missed streaming tokens based on current round only
                                if (text != null && !text.isEmpty()) {
                                    if (streamed.isEmpty()) {
                                        // Entire round's streaming was lost — use FINISHED text as fallback
                                        roundBuffer.get().append(text);
                                        lastFinalAnswer.set(text);
                                        return Flux.just(jsonEvent("token", Map.<String, Object>of("text", text)));
                                    } else if (text.length() > streamed.length() && text.startsWith(streamed)) {
                                        // Partial streaming loss — emit the delta
                                        String remaining = text.substring(streamed.length());
                                        roundBuffer.get().append(remaining);
                                        lastFinalAnswer.set(roundBuffer.get().toString());
                                        return Flux.just(jsonEvent("token", Map.<String, Object>of("text", remaining)));
                                    }
                                } else if (streamed.isEmpty()) {
                                    // Anomaly: both FINISHED text and streaming are empty
                                    log.warn("[SPAN] [{}] round:{} ANOMALY empty_round — LLM returned no content",
                                            threadId, round);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[SPAN] [{}] flatMap error (type={}): {}",
                                    threadId, so.getOutputType(), e.getMessage(), e);
                        }
                        return Flux.empty();
                    })
                    .timeout(Duration.ofMinutes(streamTimeoutMinutes))
                    .doOnError(e -> {
                        hadError.set(true);
                        if (e instanceof TimeoutException) {
                            log.warn("[SPAN] [{}] TIMEOUT stream exceeded {}min", threadId, streamTimeoutMinutes);
                        } else {
                            log.error("[SPAN] [{}] stream_error: {}", threadId, e.getMessage());
                        }
                        sseService.push(threadId, TraceEvent.spanEnd(rootSpan, "error", e.getMessage()));
                    })
                    .onErrorResume(e -> {
                        String msg = (e instanceof TimeoutException)
                                ? "响应超时，请重试"
                                : "抱歉，处理过程中出现错误: " + e.getMessage();
                        return Flux.just(jsonEvent("token", Map.<String, Object>of("text", "\n\n" + msg)));
                    })
                    .doFinally(signal -> {
                        log.info("[SPAN] [{}] stream_finally signal={}", threadId, signal);
                        if (hadError.get()) {
                            chatHistoryService.deleteLastMessage(threadId);
                        }
                        if (!hadError.get()) {
                            // Normal completion: push spanEnd + save history
                            String finalAnswer = lastFinalAnswer.get();
                            int totalRounds = roundCounter.get();
                            log.info("[SPAN] [{}] stream_complete rounds={} final_len={}",
                                    threadId, totalRounds, finalAnswer.length());
                            sseService.push(threadId, TraceEvent.spanEnd(rootSpan, "ok", null));
                            if (finalAnswer.isEmpty()) {
                                log.warn("[SPAN] [{}] ANOMALY no_final_answer rounds={}", threadId, totalRounds);
                                sseService.push(threadId,
                                        TraceEvent.error(rootSpan.spanId(), "AI 未能生成回复，请重试"));
                            } else {
                                chatHistoryService.addMessage(threadId, "assistant", finalAnswer);
                                if (chatHistoryService.getTitle(threadId) == null) {
                                    chatHistoryService.setTitle(threadId, truncate(titleHint, 20));
                                }
                            }
                        }
                        // Delay complete so downstream consumers can read buffered events
                        sseService.completeWithDelay(threadId, 100);
                        doneSink.tryEmitEmpty();
                    });

            Flux<String> eventFlux = sseService.getFlux(threadId);

            // Send SSE comments every 15s to keep the connection alive during long tool calls
            Flux<String> keepalive = Flux.interval(Duration.ofSeconds(15))
                    .map(i -> ": keepalive")
                    .takeUntilOther(doneSink.asMono());

            return Flux.merge(tokens, eventFlux, keepalive);
        } catch (GraphRunnerException e) {
            log.error("[SPAN] [{}] stream_error: {}", threadId, e.getMessage());
            sseService.complete(threadId);
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
