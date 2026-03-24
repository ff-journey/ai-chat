package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.google.common.collect.Lists;
import ff.pro.aichatali.common.HierarchicalDocSplitter;
import ff.pro.aichatali.common.ParentChunkStore;
import ff.pro.aichatali.repo.RagChunkMapper;
import ff.pro.aichatali.repo.RagChunkPo;
import ff.pro.aichatali.service.ChatHistoryService;
import ff.pro.aichatali.service.rag.BM25Service;
import ff.pro.aichatali.service.rag.RRFMerger;
import ff.pro.aichatali.tool.MemoryHybridRetrieverTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatStreamController {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    @Autowired
    @Qualifier("supervisor_agent")
    private ReactAgent supervisorAgent;

    @Autowired
    private Map<String, ReactAgent> reactAgents;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    @Qualifier("dashScopeChatModel")
    private ChatModel chatModel;

    @Autowired
    TokenTextSplitter tokenTextSplitter;
    @Autowired
    VectorStore vectorStore;
    @Autowired
    BM25Service bm25Service;
    @Autowired
    RRFMerger rrfMerger;
    @Autowired
    HierarchicalDocSplitter hierarchicalDocSplitter;
    @Autowired
    ParentChunkStore parentChunkStore;
    @Autowired
    RagChunkMapper ragChunkMapper;
    @Autowired
    EmbeddingModel embeddingModel;
    @Autowired
    private BatchingStrategy batchingStrategy;

//    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> stream(
//            @RequestParam String message,
//            @RequestParam(defaultValue = "default") String threadId) {
//        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
//        try {
//            chatHistoryService.addMessage(threadId, "user", message);
//            StringBuilder aiResponse = new StringBuilder();
//            return supervisorAgent.stream(message, config)
//                    .filter(output -> output instanceof StreamingOutput)
//                    .map(output -> {
//                        StreamingOutput so = (StreamingOutput) output;
//                        if (so.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
//                            String text = so.message().getText();
//                            aiResponse.append(text);
//                            return text;
//                        }
//                        return "";
//                    })
//                    .filter(text -> !text.isEmpty())
//                    .doOnComplete(() -> {
//                        if (!aiResponse.isEmpty()) {
//                            chatHistoryService.addMessage(threadId, "assistant", aiResponse.toString());
//                        }
//                    });
//        } catch (GraphRunnerException e) {
//            return Flux.error(e);
//        }
//    }
//
//    @PostMapping("/send")
//    public String send(@RequestParam String message,
//                       @RequestParam(defaultValue = "default") String threadId) throws Exception {
//        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
//        chatHistoryService.addMessage(threadId, "user", message);
//        String result = supervisorAgent.stream(message, config)
//                .filter(output -> output instanceof StreamingOutput)
//                .map(output -> {
//                    StreamingOutput so = (StreamingOutput) output;
//                    return so.getOutputType() == OutputType.AGENT_MODEL_STREAMING ? so.message().getText() : "";
//                })
//                .filter(text -> !text.isEmpty())
//                .reduce("", String::concat)
//                .block();
//        chatHistoryService.addMessage(threadId, "assistant", result);
//        return result;
//    }

    private boolean isImage(String contentType, String filename) {
        if (contentType != null && contentType.startsWith("image/")) return true;
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    /**
     * Multimodal endpoint: accepts message + file (multipart).
     * Saves the file to local disk and passes the path to tools via RunnableConfig metadata.
     */
    @PostMapping(value = "/multimodal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multimodal(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "threadId", defaultValue = "default") String threadId) {

        String savedPath = saveUploadedFile(file);
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        PreHandle preHandle;
        if (isImage(contentType, filename)) {
            preHandle = imagePreHandle(message, threadId, savedPath, file);
        } else {
            preHandle = filePreHandle(message, threadId, savedPath, file);
        }

        try {
            StringBuilder aiResponse = new StringBuilder();
            return graphStream(threadId, preHandle.message, preHandle.config, aiResponse);
        } catch (GraphRunnerException e) {
            return Flux.error(e);
        }
    }

    @PostMapping(value = "/upload/test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> multimodal(
            @RequestParam("file") MultipartFile file
    ) {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        return ResponseEntity.ok(filePreHandle("", "", "", file).extension);
    }

    private PreHandle filePreHandle(String message, String threadId, String savedPath, MultipartFile file) {
        //todo 前端页面需要支持展示/uploads/rag_repo/目录下的"外挂知识库", 和输入框中的Sample Library并列展示一个按钮可以点击后打开知识库文件目录, 该目录作为rag模型知识库目录
        //todo 前端页面支持知识库文件的管理, 删除知识库文件和上传知识库文件
        String fileUrl = "/uploads/rag_repo/" + Paths.get(savedPath).getFileName().toString();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("uploaded_file_path", savedPath)
                .build();

        chatHistoryService.addMessage(threadId, "user", message, fileUrl);
        List<Document> documents = parsePdfByPage(file);
        List<Document> cleanDocs = removeReferencesPages(documents);
        List<Document> chunks = tokenTextSplitter.apply(cleanDocs);

        chunks.forEach(it -> {
            var result = hierarchicalDocSplitter.split(it, file.getName());
            result.l1().forEach(l1 -> parentChunkStore.put(l1.getId(), l1));
            result.l2().forEach(l2 -> parentChunkStore.put(l2.getId(), l2));
            List<float[]> embed = embeddingModel.embed(result.l3(), EmbeddingOptions.builder().build(), batchingStrategy);
            List<RagChunkPo> poList = new ArrayList<>();
            for (int i = 0; i < result.l3().size(); i++) {
                poList.add(new RagChunkPo(result.l3().get(i), EmbeddingUtils.toList(embed.get(i))));
            }
            ragChunkMapper.batchInsert(poList);
//            vectorStore.add(result.l3());
            bm25Service.addDocuments(result.l3());
        });


//        Lists.partition(chunks, 10).forEach(batch -> {
//            vectorStore.add(batch);
//        });
        return new PreHandle(message, config, "Upload " + chunks.size() + " chunks");
    }


    private List<Document> parsePdfByPage(MultipartFile file) {
        List<Document> result = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = pdf.getNumberOfPages();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                if (text != null && !text.isBlank()) {
                    result.add(new Document(text, Map.of("page", i)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("PDF 解析失败", e);
        }
        return result;
    }

    private List<Document> removeReferencesPages(List<Document> documents) {
        List<String> keywords = List.of("参考文献", "References", "REFERENCES");

        for (int i = 0; i < documents.size(); i++) {
            String text = documents.get(i).getText();
            if (text == null) continue;

            // 关键词独占一行时认为是参考文献标题行
            boolean isRefPage = text.lines()
                    .map(String::strip)
                    .anyMatch(line -> keywords.contains(line));

            if (isRefPage) {
                log.info("上传pdf总页数: {}}, 上传pdf文档截取页数: {}", documents.size(), i);
                return documents.subList(0, i);
            }
        }
        return documents;
    }

    record PreHandle(String message, RunnableConfig config, String extension) {
        public PreHandle(String message, RunnableConfig config) {
            this(message, config, "");
        }
    }

    private PreHandle imagePreHandle(String message, String threadId, String savedPath, MultipartFile file) {
        String imageUrl = "/uploads/" + Paths.get(savedPath).getFileName().toString();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("uploaded_image_path", savedPath)
                .build();

        String imgSignal = "[用户已上传胸部X光图片，请进行肺炎影像分析]";
        if (message == null || message.isBlank()) {
            message = imgSignal;
        } else {
            message = message + "\n" + imgSignal;
        }

        chatHistoryService.addMessage(threadId, "user", message, imageUrl);
        return new PreHandle(message, config);

    }

    @NotNull
    private Flux<String> graphStream(String threadId, String finalMessage, RunnableConfig config, StringBuilder aiResponse) throws GraphRunnerException {
        return supervisorAgent.stream(finalMessage, config)
                .filter(output -> output instanceof StreamingOutput)
                .map(output -> {
                    StreamingOutput so = (StreamingOutput) output;
                    if (so.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                        String text = so.message().getText();
                        aiResponse.append(text);
                        return text;
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty())
                .doOnComplete(() -> {
                    if (!aiResponse.isEmpty()) {
                        chatHistoryService.addMessage(threadId, "assistant", aiResponse.toString());
                    }
                });
    }

    @Value("${app.samples.dir:samples}")
    private String samplesDir;

    /**
     * Multimodal endpoint using a pre-stored sample image instead of file upload.
     */
    @PostMapping(value = "/multimodal/sample", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multimodalSample(
            @RequestParam("sampleId") String sampleId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "threadId", defaultValue = "default") String threadId) {

        // sampleId format: "category/filename"
        Path samplePath = Paths.get(samplesDir, sampleId);
        if (!Files.exists(samplePath) || !Files.isRegularFile(samplePath)) {
            return Flux.error(new RuntimeException("Sample image not found: " + sampleId));
        }

        String absolutePath = samplePath.toAbsolutePath().toString();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata("uploaded_image_path", absolutePath)
                .build();

        String imgSignal = "[用户已上传胸部X光图片，请进行肺炎影像分析]";
        if (message == null || message.isBlank()) {
            message = "我拍了胸部x光, 帮我看下是否有问题\n" + imgSignal;
        } else {
            message = message + "\n" + imgSignal;
        }


        // Encode sampleId path components so URLs with spaces remain valid
        String sampleImageUrl = "/samples/" + Arrays.stream(sampleId.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        chatHistoryService.addMessage(threadId, "user", message, sampleImageUrl);

        try {
            StringBuilder aiResponse = new StringBuilder();
            final String finalMessage = message;
            return supervisorAgent.stream(finalMessage, config)
                    .filter(output -> output instanceof StreamingOutput)
                    .map(output -> {
                        StreamingOutput so = (StreamingOutput) output;
                        if (so.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                            String text = so.message().getText();
                            aiResponse.append(text);
                            return text;
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(() -> {
                        if (!aiResponse.isEmpty()) {
                            chatHistoryService.addMessage(threadId, "assistant", aiResponse.toString());
                        }
                    });
        } catch (GraphRunnerException e) {
            return Flux.error(e);
        }
    }

    /**
     * Returns the message history for a thread.
     * Reads from the supervisor agent's MemorySaver first; falls back to ChatHistoryService.
     * Each entry: {"messageType": "user"|"assistant", "content": "..."}
     */
    @GetMapping("/history/{threadId}")
    public ResponseEntity<List<Map<String, Object>>> getThreadHistory(
            @PathVariable String threadId,
            @RequestParam(value = "appName", required = false) String appName) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Pre-collect per-user-message imageUrls from ChatHistoryService (ordered by insertion)
        List<String> imageUrls = chatHistoryService.getHistory(threadId).stream()
                .filter(r -> "user".equals(r.role()))
                .map(ChatHistoryService.MessageRecord::imageUrl)
                .toList();

        try {
            ReactAgent targetAgent = (appName != null && reactAgents.containsKey(appName))
                    ? reactAgents.get(appName)
                    : supervisorAgent;
            CompiledGraph compiledGraph = targetAgent.getCompiledGraph();
            if (compiledGraph != null) {
                RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
                Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(config);
                if (snapshotOpt.isPresent()) {
                    OverAllState state = snapshotOpt.get().state();
                    Object messagesObj = state.data().get("messages");
                    if (messagesObj instanceof List<?> rawMessages) {
                        int userIdx = 0;
                        for (Object rawMsg : rawMessages) {
                            if (rawMsg instanceof UserMessage userMsg) {
                                Map<String, Object> entry = new HashMap<>();
                                entry.put("messageType", "user");
                                entry.put("content", userMsg.getText() != null ? userMsg.getText() : "");
                                // Attach imageUrl from ChatHistoryService if available
                                if (userIdx < imageUrls.size() && imageUrls.get(userIdx) != null) {
                                    entry.put("media", List.of(
                                            Map.of("mimeType", "image/jpeg", "data", imageUrls.get(userIdx))
                                    ));
                                }
                                result.add(entry);
                                userIdx++;
                            } else if (rawMsg instanceof AssistantMessage assistantMsg) {
                                if (!assistantMsg.hasToolCalls()) {
                                    String text = assistantMsg.getText();
                                    if (text != null && !text.isEmpty()) {
                                        result.add(Map.of("messageType", "assistant", "content", text));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load history from MemorySaver for thread {}: {}", threadId, e.getMessage());
        }

        // Fallback: ChatHistoryService (used by /api/chat/stream and /api/chat/multimodal)
        if (result.isEmpty()) {
            int[] userIdx = {0};
            chatHistoryService.getHistory(threadId).forEach(record -> {
                String messageType = "user".equals(record.role()) ? "user" : "assistant";
                Map<String, Object> entry = new HashMap<>();
                entry.put("messageType", messageType);
                entry.put("content", record.content());
                if ("user".equals(record.role())) {
                    if (record.imageUrl() != null) {
                        entry.put("media", List.of(
                                Map.of("mimeType", "image/jpeg", "data", record.imageUrl())
                        ));
                    }
                    userIdx[0]++;
                }
                result.add(entry);
            });
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Generates a concise LLM summary (≤15 chars) for a conversation thread.
     * Called by the frontend every 5 user turns to produce a human-readable thread title.
     * Request: { "messages": [{ "role": "user|assistant", "content": "..." }] }
     * Response: { "summary": "..." }
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> generateSummary(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
        if (messages == null || messages.isEmpty()) {
            return ResponseEntity.ok(Map.of("summary", "新对话"));
        }

        StringBuilder conv = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = msg.getOrDefault("role", "user");
            String content = msg.getOrDefault("content", "");
            if (!content.isBlank()) {
                String prefix = "user".equals(role) ? "用户：" : "助手：";
                conv.append(prefix)
                        .append(content, 0, Math.min(content.length(), 300))
                        .append("\n");
            }
        }

        String promptText = "请用一句话（15字以内）总结以下对话的核心主题，作为对话标题，只输出标题文字，不加引号或其他任何内容：\n" + conv;
        try {
            String summary = chatModel.call(new Prompt(promptText, DashScopeChatOptions.builder().enableThinking(false).build()))
                    .getResult().getOutput().getText().trim();
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            log.warn("Failed to generate thread summary: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("summary", "对话摘要"));
        }
    }

    private String saveUploadedFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(System.getProperties().get("user.dir").toString(), uploadDir);
            Files.createDirectories(uploadPath);
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path dest = uploadPath.resolve(filename);
            file.transferTo(dest.toFile());
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save uploaded file", e);
        }
    }


    @GetMapping("/any/test")
    public void anyTest() {
        MemoryHybridRetrieverTool memoryHybridRetrieverTool = new MemoryHybridRetrieverTool(vectorStore, bm25Service, rrfMerger);
        memoryHybridRetrieverTool.apply(new MemoryHybridRetrieverTool.Input("糖尿病慢性并发症"), null);
    }
}
