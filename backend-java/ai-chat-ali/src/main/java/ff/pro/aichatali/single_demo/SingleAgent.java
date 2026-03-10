package ff.pro.aichatali.single_demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * @author journey
 * @date 2026/3/10
 **/
public class SingleAgent {

    // 初始化 ChatModel
    DashScopeApi dashScopeApi = DashScopeApi.builder()
            .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
//            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .build();

    ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .build();

    // 定义天气查询工具
    public class WeatherTool implements BiFunction<String, ToolContext, String> {
        @Override
        public String apply(String city, ToolContext toolContext) {
            return "It's always sunny in " + city + "!";
        }
    }

    ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
            .description("Get weather for a given city")
            .inputType(String.class)
            .build();

    // 创建 agent
    ReactAgent agent = ReactAgent.builder()
            .name("weather_agent")
            .model(chatModel)
            .tools(weatherTool)
            .systemPrompt("You are a helpful assistant")
            .saver(new MemorySaver())
            .build();
    // 用户位置工具 - 使用上下文
    public class UserLocationTool implements BiFunction<String, ToolContext, String> {
        @Override
        public String apply(
                @ToolParam(description = "User query") String query,
                ToolContext toolContext
        ) {
            // 从上下文中获取用户信息
            String userId = "";
            if (toolContext != null && toolContext.getContext() != null) {
                RunnableConfig runnableConfig = (RunnableConfig) toolContext.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
                Optional<Object> userIdObjOptional = runnableConfig.metadata("user_id");
                if (userIdObjOptional.isPresent()) {
                    userId = (String) userIdObjOptional.get();
                }
            }
            if (userId == null) {
                userId = "1";
            }
            return "1".equals(userId) ? "Florida" : "San Francisco";
        }
    }
    ToolCallback getUserLocationTool = FunctionToolCallback
            .builder("getUserLocation", new UserLocationTool())
            .description("Retrieve user location based on user ID")
            .inputType(String.class)
            .build();
    public void run() throws GraphRunnerException {
        // 运行 agent
        AssistantMessage response = agent.call("what is the weather in San Francisco");
        System.out.println(response.getText());
    }

    public static void main(String[] args) {

        var agent = new SingleAgent();
        try {
            agent.run();
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }
}
