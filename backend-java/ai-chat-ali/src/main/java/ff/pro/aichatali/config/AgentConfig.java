package ff.pro.aichatali.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import ff.pro.aichatali.service.ToolRegistryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.observation.DefaultAdvisorObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiFunction;

@Configuration
public class AgentConfig {

    @Bean("generalChatClient")
    @Primary
    public ChatClient generalChatClient(@Qualifier("dashScopeChatModel") DashScopeChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个通用助手")
                .build();
    }

    @Bean("weather_agent")
    public ReactAgent weatherAgent(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        ToolCallback weatherTool = FunctionToolCallback
                .builder("get_weather", (BiFunction<String, ToolContext, String>) (city, ctx) -> "Sunny in " + city + "!")
                .description("Get weather for a city")
                .inputType(String.class)
                .build();
        return ReactAgent.builder()
                .advisorObservationConvention(new DefaultAdvisorObservationConvention())
                .name("weather_agent")
                .model(chatModel)
                .tools(List.of(weatherTool))
                .systemPrompt("You are a weather assistant. Use get_weather tool to answer.")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("chat_agent")
    public ReactAgent chatAgent(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .systemPrompt("你是一个私人助理")
                .description("处理所有用户输入，包括闲聊、问候、询问助手能力、以及无法分类的任何问题")
                .saver(new MemorySaver())
                .build();
    }

    @Bean("supervisor_agent")
    public ReactAgent supervisorAgent(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel,
            ToolRegistryService toolRegistryService) {

        String prompt = """
                当前时间：{{current_time}}
                你是一个优秀的私人助理, 任何动作前都会分析用户意图，并调用合适的工具。
                任何动作前都首先分析用户意图, 并查看上下文是否已有足够信息做出直接回复。
                 工具选择规则：
                 - 消息包含 [用户已上传胸部X光图片] 且用户需要诊断建议/分析报告 → 调用 medical_diagnosis（内部自动读取影像）
                 - 消息包含 [用户已上传胸部X光图片] 且用户只需快速判断是否肺炎 → 调用 pneumoniaCnnTool
                 - 用户咨询医疗问题、症状、用药、诊断建议（无图片）→ 汇总病情信息后调用 medical_diagnosis
                 - 用户提到胸片、肺炎、影像分析但没有图片标记 → 调用 medical_diagnosis，工具会告知用户需要上传图片
                 - 用户进行日常闲聊、问候、或非医疗类问题 → 调用普通对话工具
                 - 无法判断意图时 → 视为普通聊天, 可自行回复

                 注意事项：
                 - 每次只调用一个工具
                 - 收到工具结果后，直接返回给用户，医疗诊断结果适当加工友好回复, **如果病情信息太少导致判断模糊, 需要主动提问更多信息**
                 - 不要向用户解释你的工作流程和工具规则
                 - 不要向用户介绍你自己能做什么
                 - 如果工具返回包含"请上传"等提示信息，直接以友好的中文转达给用户
                 - 如果医疗诊断结果病情信息不足, 可主动询问用户更多细节
                """.replace("{{current_time}}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return ReactAgent.builder()
                .name("supervisor_agent")
                .model(dashScopeChatModel)
                .tools(toolRegistryService.getToolCallbacks())
                .systemPrompt(prompt)
                .saver(new MemorySaver())
                .build();
    }
}
