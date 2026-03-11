package ff.pro.aichatali.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String threadId) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        try {
            return supervisorAgent.stream(message, config)
                    .filter(output -> output instanceof StreamingOutput)
                    .map(output -> {
                        StreamingOutput so = (StreamingOutput) output;
                        if (so.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                            return so.message().getText();
                        }
                        return "";
                    })
                    .filter(text -> !text.isEmpty());
        } catch (GraphRunnerException e) {
            return Flux.error(e);
        }
    }

    @PostMapping("/send")
    public String send(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String threadId) throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        return supervisorAgent.call(message, config).getText();
    }
}
