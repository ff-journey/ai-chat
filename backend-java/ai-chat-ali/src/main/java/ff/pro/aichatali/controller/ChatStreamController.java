package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import ff.pro.aichatali.dto.MediaItem;
import ff.pro.aichatali.dto.MultimodalRequest;
import ff.pro.aichatali.service.ChatHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;



@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    @Autowired
    @Qualifier("supervisor_agent")
    private ReactAgent supervisorAgent;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String threadId) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        try {
            chatHistoryService.addMessage(threadId, "user", message);
            StringBuilder aiResponse = new StringBuilder();
            return supervisorAgent.stream(message, config)
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

    @PostMapping("/send")
    public String send(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String threadId) throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        chatHistoryService.addMessage(threadId, "user", message);
        String result = supervisorAgent.call(message, config).getText();
        chatHistoryService.addMessage(threadId, "assistant", result);
        return result;
    }

    /**
     * Multimodal endpoint: accepts message + media (base64 images).
     * Stores uploaded image in RunnableConfig metadata so tools can access it.
     */
    @PostMapping(value = "/multimodal", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multimodal(@RequestBody MultimodalRequest request) {
        var configBuilder = RunnableConfig.builder()
                .threadId(request.getThreadId());

        // Extract first image from media list and put into metadata for tools
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {
            MediaItem first = request.getMedia().get(0);
            configBuilder.addMetadata("uploaded_image_base64", first.getData());
            configBuilder.addMetadata("uploaded_image_mime_type", first.getMimeType());
        }

        RunnableConfig config = configBuilder.build();

        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            message = "Please analyze the uploaded image.";
        }

        chatHistoryService.addMessage(request.getThreadId(), "user", message);

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
                            chatHistoryService.addMessage(
                                    request.getThreadId(), "assistant", aiResponse.toString());
                        }
                    });
        } catch (GraphRunnerException e) {
            return Flux.error(e);
        }
    }

}
