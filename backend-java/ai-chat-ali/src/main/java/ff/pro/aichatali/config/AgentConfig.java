package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Configuration
public class AgentConfig {

    @Bean("weather_agent")
    public ReactAgent weatherAgent(ChatModel chatModel) {
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", (BiFunction<String, ToolContext, String>) (city, ctx) -> "Sunny in " + city + "!")
                .description("Get weather for a city")
                .inputType(String.class)
                .build();
        return ReactAgent.builder()
                .name("weather_agent")
                .model(chatModel)
                .tools(List.of(weatherTool))
                .systemPrompt("You are a weather assistant. Use get_weather tool to answer.")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("chat_agent")
    public ReactAgent chatAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .systemPrompt("You are a helpful general-purpose assistant.")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("supervisor_agent")
    public ReactAgent supervisorAgent(ChatModel chatModel,
            @Qualifier("weather_agent") ReactAgent weatherAgent,
            @Qualifier("chat_agent") ReactAgent chatAgent) {

        ToolCallback weatherAgentTool = FunctionToolCallback
                .builder("weather_agent", (BiFunction<String, ToolContext, String>) (query, ctx) -> {
                    try {
                        RunnableConfig config = RunnableConfig.builder().threadId("supervisor-weather").build();
                        return weatherAgent.call(query, config).getText();
                    } catch (Exception e) {
                        return "Weather agent error: " + e.getMessage();
                    }
                })
                .description("Delegate to weather agent for weather and location queries")
                .inputType(String.class)
                .build();

        ToolCallback chatAgentTool = FunctionToolCallback
                .builder("chat_agent", (BiFunction<String, ToolContext, String>) (query, ctx) -> {
                    try {
                        RunnableConfig config = RunnableConfig.builder().threadId("supervisor-chat").build();
                        return chatAgent.call(query, config).getText();
                    } catch (Exception e) {
                        return "Chat agent error: " + e.getMessage();
                    }
                })
                .description("Delegate to chat agent for general conversation")
                .inputType(String.class)
                .build();

        return ReactAgent.builder()
                .name("supervisor_agent")
                .model(chatModel)
                .tools(List.of(weatherAgentTool, chatAgentTool))
                .systemPrompt("""
                        You are a supervisor routing requests to specialized agents.
                        - For weather/location queries: use weather_agent
                        - For general conversation: use chat_agent
                        Route the user's request to the appropriate agent.
                        """)
                .saver(new MemorySaver())
                .build();
    }

    @Bean
    public AgentLoader agentLoader(
            @Qualifier("weather_agent") ReactAgent weatherAgent,
            @Qualifier("chat_agent") ReactAgent chatAgent,
            @Qualifier("supervisor_agent") ReactAgent supervisorAgent) {
        Map<String, Agent> agents = Map.of(
                "weather_agent", weatherAgent,
                "chat_agent", chatAgent,
                "supervisor_agent", supervisorAgent
        );
        return new AgentLoader() {
            @Override
            public List<String> listAgents() {
                return List.copyOf(agents.keySet());
            }
            @Override
            public Agent loadAgent(String name) {
                return agents.get(name);
            }
        };
    }
}
