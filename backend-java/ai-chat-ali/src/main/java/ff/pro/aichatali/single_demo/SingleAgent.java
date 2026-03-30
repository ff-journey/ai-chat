//package ff.pro.aichatali.single_demo;
//
//import org.springframework.ai.openai.OpenAiChatModel;
//import org.springframework.ai.openai.OpenAiChatOptions;
//import org.springframework.ai.openai.api.OpenAiApi;
//import com.alibaba.cloud.ai.graph.NodeOutput;
//import com.alibaba.cloud.ai.graph.RunnableConfig;
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
//import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
//import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
//import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
//import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
//import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
//import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
//import com.alibaba.cloud.ai.graph.streaming.OutputType;
//import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.model.ToolContext;
//import org.springframework.ai.tool.ToolCallback;
//import org.springframework.ai.tool.annotation.ToolParam;
//import org.springframework.ai.tool.function.FunctionToolCallback;
//import reactor.core.publisher.Flux;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.function.BiFunction;
//
//import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;
//
///**
// * @author journey
// * @date 2026/3/10
// **/
//public class SingleAgent {
//
//    // 初始化 ChatModel（SiliconFlow OpenAI-compatible API）
//    ChatModel chatModel = OpenAiChatModel.builder()
//            .openAiApi(OpenAiApi.builder()
//                    .apiKey(System.getenv("SILICONFLOW_API_KEY"))
//                    .baseUrl("https://api.siliconflow.cn")
//                    .build())
//            .defaultOptions(OpenAiChatOptions.builder()
//                    .model("deepseek-ai/DeepSeek-V3")
//                    .build())
//            .build();
//
//    // 定义天气查询工具
//    public class WeatherTool implements BiFunction<String, ToolContext, String> {
//        @Override
//        public String apply(String city, ToolContext toolContext) {
//            return "It's always sunny in " + city + "!";
//        }
//    }
//
//    ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
//            .description("Get weather for a given city")
//            .inputType(String.class)
//            .build();
//
//    ToolCallback getUserLocationTool = FunctionToolCallback
//            .builder("getUserLocation", new UserLocationTool())
//            .description("Retrieve user location based on user ID")
//            .inputType(String.class)
//            .build();
//
//    // 创建 agent
//    ReactAgent agent = ReactAgent.builder()
//            .name("weatherAgent")
//            .model(chatModel)
//            .tools(List.of(weatherTool, getUserLocationTool))
//            .systemPrompt("You are a helpful assistant")
//            .saver(new MemorySaver())
//            .interceptors(List.of(new ToolErrorInterceptor()))
//            .build();
//    // 用户位置工具 - 使用上下文
//    public class UserLocationTool implements BiFunction<String, ToolContext, String> {
//        @Override
//        public String apply(
//                @ToolParam(description = "User query") String query,
//                ToolContext toolContext
//        ) {
//            // 从上下文中获取用户信息
//            String userId = "";
//            if (toolContext != null && toolContext.getContext() != null) {
//                RunnableConfig runnableConfig = (RunnableConfig) toolContext.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
//                Optional<Object> userIdObjOptional = runnableConfig.metadata("user_id");
//                if (userIdObjOptional.isPresent()) {
//                    userId = (String) userIdObjOptional.get();
//                }
//            }
//            if (userId == null) {
//                userId = "1";
//            }
//            return "1".equals(userId) ? "Florida" : "San Francisco";
//        }
//    }
//    public class ToolErrorInterceptor extends ToolInterceptor {
//        @Override
//        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
//            try {
//                return handler.call(request);
//            } catch (Exception e) {
//                return ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
//                        "Tool failed: " + e.getMessage());
//            }
//        }
//
//        @Override
//        public String getName() {
//            return "ToolErrorInterceptor";
//        }
//    }
//    public void run() throws GraphRunnerException {
//        RunnableConfig config = RunnableConfig.builder()
//                .threadId("thread_id_1")
//                .addMetadata("user_id", "1")
//                .build();
//        // 运行 agent
////        AssistantMessage response = agent.call("where am i", config);
////        System.out.println(response.getText());
//
//        Flux<NodeOutput> stream = agent.stream("where am i", config);
//        stream.subscribe(
//                output -> {
//                    // 检查是否为 StreamingOutput 类型
//                    if (output instanceof StreamingOutput streamingOutput) {
//                        OutputType type = streamingOutput.getOutputType();
//
//                        // 处理模型推理的流式输出
//                        if (type == OutputType.AGENT_MODEL_STREAMING) {
//                            // 流式增量内容，逐步显示
//                            System.out.print(streamingOutput.message().getText());
//                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
//                            // 模型推理完成，可获取完整响应
//                            System.out.println("\n模型输出完成");
//                        }
//
//                        // 处理工具调用完成（目前不支持 STREAMING）
//                        if (type == OutputType.AGENT_TOOL_FINISHED) {
//                            System.out.println("工具调用完成: " + output.node());
//                        }
//
//                        // 对于 Hook 节点，通常只关注完成事件（如果Hook没有有效输出可以忽略）
//                        if (type == OutputType.AGENT_HOOK_FINISHED) {
//                            System.out.println("Hook 执行完成: " + output.node());
//                        }
//                    }
//                },
//                error -> System.err.println("错误: " + error),
//                () -> System.out.println("Agent 执行完成")
//        );
//        stream.blockLast();
//    }
//
//
//
//
//    public static void main(String[] args) {
//
//        var agent = new SingleAgent();
//        try {
//            agent.run();
//        } catch (GraphRunnerException e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
